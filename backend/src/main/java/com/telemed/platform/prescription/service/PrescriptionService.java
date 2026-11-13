package com.telemed.platform.prescription.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.prescription.api.PrescriptionResponse;
import com.telemed.platform.prescription.api.PatientPrescriptionResponse;
import com.telemed.platform.prescription.domain.Prescription;
import com.telemed.platform.prescription.domain.PrescriptionStatus;
import com.telemed.platform.prescription.domain.PrescriptionRevision;
import com.telemed.platform.prescription.mapper.PrescriptionMapper;
import com.telemed.platform.prescription.repository.PrescriptionRepository;
import com.telemed.platform.prescription.repository.PrescriptionRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PrescriptionService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionRevisionRepository revisionRepository;
    private final PrescriptionMapper prescriptionMapper;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public PrescriptionService(
            AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            PrescriptionRepository prescriptionRepository,
            PrescriptionRevisionRepository revisionRepository,
            PrescriptionMapper prescriptionMapper,
            Clock clock,
            ZoneId appointmentZone) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.revisionRepository = revisionRepository;
        this.prescriptionMapper = prescriptionMapper;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    @Transactional
    public PrescriptionResponse createDraft(String doctorEmail, UUID appointmentId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new InvalidRequestException("Le contenu de l'ordonnance est obligatoire.");
        }
        if (content.length() > 10000) {
            throw new InvalidRequestException("Le contenu de l'ordonnance est trop long.");
        }
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
        if (!appointment.getDoctor().getUser().getEmail().equalsIgnoreCase(doctorEmail)) {
            throw new NotFoundException("Rendez-vous introuvable.");
        }
        if (!PrescriptionDraftEligibility.isAllowed(appointment, clock, appointmentZone)) {
            throw new InvalidRequestException(
                    "Le brouillon d'ordonnance est disponible pendant le rendez-vous confirmé ou après sa fin.");
        }
        if (prescriptionRepository.findByAppointmentId(appointmentId).isPresent()) {
            throw new ConflictException("Une ordonnance existe déjà pour ce rendez-vous.");
        }
        Prescription prescription = prescriptionRepository.save(new Prescription(appointment, content));
        addRevision(prescription, appointment.getDoctor().getUser().getId(), "CREATED");
        return prescriptionMapper.toResponse(prescription);
    }

    @Transactional(readOnly = true)
    public Optional<PrescriptionResponse> findForDoctorAppointment(String doctorEmail, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .filter(candidate -> candidate.getDoctor().getUser().getEmail().equalsIgnoreCase(doctorEmail))
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
        return prescriptionRepository.findByAppointmentId(appointment.getId())
                .map(prescriptionMapper::toResponse);
    }

    @Transactional
    public PrescriptionResponse createGeneratedDraft(String doctorEmail, UUID appointmentId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new InvalidRequestException("Le contenu du brouillon est obligatoire.");
        }
        if (content.length() > 10000) {
            throw new InvalidRequestException("Le contenu du brouillon est trop long.");
        }
        Appointment appointment = appointmentRepository.findById(appointmentId)
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
        Prescription prescription = prescriptionRepository.save(new Prescription(appointment, content));
        addRevision(prescription, appointment.getDoctor().getUser().getId(), "GENERATED");
        return prescriptionMapper.toResponse(prescription);
    }

    @Transactional
    public PrescriptionResponse updateDraft(String doctorEmail, UUID prescriptionId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new InvalidRequestException("Le contenu du brouillon est obligatoire.");
        }
        if (content.length() > 10000) {
            throw new InvalidRequestException("Le contenu du brouillon est trop long.");
        }
        Prescription prescription = findDoctorPrescription(doctorEmail, prescriptionId);
        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new InvalidRequestException("Seul un brouillon peut être modifié.");
        }
        if (!PrescriptionDraftEligibility.isAllowed(
                prescription.getAppointment(), clock, appointmentZone)) {
            throw new InvalidRequestException(
                    "Le brouillon ne peut être modifié que pendant le rendez-vous confirmé ou après sa fin.");
        }
        prescription.updateDraft(content);
        addRevision(prescription, prescription.getAppointment().getDoctor().getUser().getId(), "EDITED");
        return prescriptionMapper.toResponse(prescription);
    }

    @Transactional
    public PrescriptionResponse validate(String doctorEmail, UUID prescriptionId) {
        Prescription prescription = findDoctorPrescription(doctorEmail, prescriptionId);
        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new InvalidRequestException("Cette ordonnance est déjà validée.");
        }
        if (!PrescriptionDraftEligibility.isAllowed(
                prescription.getAppointment(), clock, appointmentZone)) {
            throw new InvalidRequestException(
                    "L'ordonnance peut être validée pendant le rendez-vous confirmé ou après sa fin.");
        }
        UUID doctorUserId = prescription.getAppointment().getDoctor().getUser().getId();
        prescription.validateOn(LocalDate.now(clock));
        addRevision(prescription, doctorUserId, "VALIDATED");
        return prescriptionMapper.toResponse(prescription);
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse resign(String doctorEmail, UUID prescriptionId) {
        Prescription prescription = findDoctorPrescription(doctorEmail, prescriptionId);
        if (prescription.getStatus() != PrescriptionStatus.VALIDATED) {
            throw new InvalidRequestException("Seule une ordonnance validée peut être téléchargée.");
        }
        return prescriptionMapper.toResponse(prescription);
    }

    @Transactional(readOnly = true)
    public List<com.telemed.platform.prescription.api.PrescriptionRevisionResponse> history(
            String doctorEmail, UUID prescriptionId) {
        Prescription prescription = findDoctorPrescription(doctorEmail, prescriptionId);
        return revisionRepository.findAllByPrescriptionIdOrderByRevisionNumberAsc(prescription.getId()).stream()
                .map(prescriptionMapper::toRevisionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Prescription findFinalForDoctor(String doctorEmail, UUID prescriptionId) {
        Prescription prescription = findDoctorPrescription(doctorEmail, prescriptionId);
        if (prescription.getStatus() != PrescriptionStatus.VALIDATED) {
            throw new InvalidRequestException("Le PDF n'est disponible qu'après validation de l'ordonnance.");
        }
        return prescription;
    }

    @Transactional(readOnly = true)
    public Prescription findFinalForPatient(String patientEmail, UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findByIdAndAppointmentPatientUserEmailIgnoreCaseAndStatus(
                        prescriptionId, patientEmail, PrescriptionStatus.VALIDATED)
                .orElseThrow(() -> new NotFoundException("Ordonnance introuvable."));
        return prescription;
    }

    @Transactional(readOnly = true)
    public List<PatientPrescriptionResponse> findPatientPrescriptions(String patientEmail) {
        Patient patient = patientRepository.findByUserEmail(patientEmail)
                .orElseThrow(() -> new NotFoundException("Profil patient introuvable."));
        return prescriptionRepository
                .findAllByAppointmentPatientIdAndStatusOrderByAppointmentScheduledAtDesc(
                        patient.getId(), PrescriptionStatus.VALIDATED).stream()
                .map(prescription -> {
                    var appointment = prescription.getAppointment();
                    var doctor = appointment.getDoctor();
                    String doctorName = java.util.stream.Stream.of(doctor.getFirstName(), doctor.getLastName())
                            .filter(StringUtils::hasText)
                            .collect(java.util.stream.Collectors.joining(" "));
                    if (!StringUtils.hasText(doctorName)) {
                        doctorName = "Médecin";
                    }
                    return new PatientPrescriptionResponse(
                            prescription.getId(),
                            appointment.getId(),
                            doctor.getId(),
                            doctorName,
                            doctor.getSpecialty(),
                            prescription.getContent(),
                            prescription.getDate(),
                            appointment.getScheduledAt());
                })
                .toList();
    }

    private Prescription findDoctorPrescription(String doctorEmail, UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Ordonnance introuvable."));
        if (!prescription.getAppointment().getDoctor().getUser().getEmail().equalsIgnoreCase(doctorEmail)) {
            throw new NotFoundException("Ordonnance introuvable.");
        }
        return prescription;
    }

    private void addRevision(Prescription prescription, UUID actorUserId, String changeType) {
        int revisionNumber = revisionRepository.findMaxRevisionNumber(prescription.getId()) + 1;
        revisionRepository.save(new PrescriptionRevision(
                prescription,
                revisionNumber,
                prescription.getContent(),
                actorUserId,
                changeType,
                clock.instant()));
    }

}
