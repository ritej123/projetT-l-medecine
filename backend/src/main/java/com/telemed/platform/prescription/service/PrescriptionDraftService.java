package com.telemed.platform.prescription.service;

import java.util.UUID;
import java.time.Clock;
import java.time.ZoneId;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.prescription.api.PrescriptionResponse;
import com.telemed.platform.prescription.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PrescriptionDraftService {

    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicalNoteAnonymizer anonymizer;
    private final PrescriptionDraftGenerator draftGenerator;
    private final PrescriptionService prescriptionService;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public PrescriptionDraftService(
            AppointmentRepository appointmentRepository,
            PrescriptionRepository prescriptionRepository,
            MedicalNoteAnonymizer anonymizer,
            PrescriptionDraftGenerator draftGenerator,
            PrescriptionService prescriptionService,
            Clock clock,
            ZoneId appointmentZone) {
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.anonymizer = anonymizer;
        this.draftGenerator = draftGenerator;
        this.prescriptionService = prescriptionService;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    public PrescriptionResponse generate(String doctorEmail, UUID appointmentId, String consultationNotes) {
        if (!StringUtils.hasText(consultationNotes)) {
            throw new InvalidRequestException("Les notes de consultation sont obligatoires.");
        }
        if (consultationNotes.length() > 10000) {
            throw new InvalidRequestException("Les notes de consultation sont trop longues.");
        }
        Appointment appointment = appointmentRepository.findWithDoctorUserById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
        if (!appointment.getDoctor().getUser().getEmail().equalsIgnoreCase(doctorEmail)) {
            throw new NotFoundException("Rendez-vous introuvable.");
        }
        if (!PrescriptionDraftEligibility.isAllowed(appointment, clock, appointmentZone)) {
            throw new InvalidRequestException(
                    "Le brouillon exige un rendez-vous terminé ou une consultation confirmée en cours.");
        }
        if (prescriptionRepository.findByAppointmentId(appointmentId).isPresent()) {
            throw new ConflictException("Une ordonnance existe déjà pour ce rendez-vous.");
        }
        String anonymizedNotes = anonymizer.anonymize(consultationNotes);
        String generatedContent = anonymizer.anonymize(draftGenerator.generateDraft(anonymizedNotes));
        if (generatedContent.length() > 10000) {
            throw new ExternalLlmUnavailableException("Le brouillon généré dépasse la taille autorisée.");
        }
        return prescriptionService.createGeneratedDraft(doctorEmail, appointmentId, generatedContent);
    }
}
