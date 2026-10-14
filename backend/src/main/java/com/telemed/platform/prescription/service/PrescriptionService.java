package com.telemed.platform.prescription.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.prescription.api.PrescriptionResponse;
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
    private final PrescriptionSignatureService signatureService;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public PrescriptionService(
            AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            PrescriptionRepository prescriptionRepository,
            PrescriptionRevisionRepository revisionRepository,
            PrescriptionMapper prescriptionMapper,
            PrescriptionSignatureService signatureService,
            Clock clock,
            ZoneId appointmentZone) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.revisionRepository = revisionRepository;
        this.prescriptionMapper = prescriptionMapper;
        this.signatureService = signatureService;
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
        if (appointment.getStatus() != AppointmentStatus.DONE) {
            throw new InvalidRequestException("Une ordonnance ne peut être créée qu'après le rendez-vous.");
        }
        if (prescriptionRepository.findByAppointmentId(appointmentId).isPresent()) {
            throw new ConflictException("Une ordonnance existe déjà pour ce rendez-vous.");
        }
        Prescription prescription = prescriptionRepository.save(new Prescription(appointment, content));
        addRevision(prescription, appointment.getDoctor().getUser().getId(), "CREATED");
        return prescriptionMapper.toResponse(prescription);
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse findForDoctorAppointment(String doctorEmail, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .filter(candidate -> candidate.getDoctor().getUser().getEmail().equalsIgnoreCase(doctorEmail))
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
        Prescription prescription = prescriptionRepository.findByAppointmentId(appointment.getId())
                .orElseThrow(() -> new NotFoundException("Aucune ordonnance n'est enregistrée."));
        return prescriptionMapper.toResponse(prescription);
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
        Instant signedAt = clock.instant();
        String contentSha256 = sha256(prescription.getContent());
        UUID doctorUserId = prescription.getAppointment().getDoctor().getUser().getId();
        String digitalSignature = signatureService.sign(
                prescription.getId(), doctorUserId, signedAt.toString(), contentSha256);
        prescription.validateOn(
                LocalDate.now(clock), doctorUserId, signedAt, contentSha256, digitalSignature);
        addRevision(prescription, doctorUserId, "SIGNED");
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
            throw new InvalidRequestException("Le PDF n'est disponible qu'après validation et signature.");
        }
        if (prescription.getSignedAt() == null
                || prescription.getSignedByUserId() == null
                || prescription.getContentSha256() == null
                || prescription.getDigitalSignature() == null) {
            throw new InvalidRequestException("Cette ordonnance historique ne possède pas de signature vérifiable.");
        }
        return prescription;
    }

    @Transactional(readOnly = true)
    public Prescription findFinalForPatient(String patientEmail, UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findByIdAndAppointmentPatientUserEmailIgnoreCaseAndStatus(
                        prescriptionId, patientEmail, PrescriptionStatus.VALIDATED)
                .orElseThrow(() -> new NotFoundException("Ordonnance introuvable."));
        if (prescription.getSignedAt() == null
                || prescription.getSignedByUserId() == null
                || prescription.getContentSha256() == null
                || prescription.getDigitalSignature() == null) {
            throw new NotFoundException("Ordonnance introuvable.");
        }
        return prescription;
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> findPatientPrescriptions(String patientEmail) {
        Patient patient = patientRepository.findByUserEmail(patientEmail)
                .orElseThrow(() -> new NotFoundException("Profil patient introuvable."));
        return prescriptionRepository
                .findAllByAppointmentPatientIdAndStatusOrderByAppointmentScheduledAtDesc(
                        patient.getId(), PrescriptionStatus.VALIDATED).stream()
                .map(prescriptionMapper::toResponse)
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
        int revisionNumber = revisionRepository.countByPrescriptionId(prescription.getId()) + 1;
        revisionRepository.save(new PrescriptionRevision(
                prescription,
                revisionNumber,
                prescription.getContent(),
                actorUserId,
                changeType,
                clock.instant()));
    }

    private String sha256(String content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
