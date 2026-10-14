package com.telemed.platform.consultation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.consultation.api.ConsultationRequest;
import com.telemed.platform.consultation.api.ConsultationResponse;
import com.telemed.platform.consultation.api.ConsultationRevisionResponse;
import com.telemed.platform.consultation.domain.Consultation;
import com.telemed.platform.consultation.domain.ConsultationRevision;
import com.telemed.platform.consultation.repository.ConsultationRepository;
import com.telemed.platform.consultation.repository.ConsultationRevisionRepository;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.notification.service.AppointmentNotificationService;
import com.telemed.platform.notification.service.DoctorNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultationService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final ConsultationRevisionRepository revisionRepository;
    private final Clock clock;
    private final AppointmentNotificationService appointmentNotificationService;
    private final DoctorNotificationService doctorNotificationService;
    private final ZoneId appointmentZone;

    public ConsultationService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            ConsultationRepository consultationRepository,
            ConsultationRevisionRepository revisionRepository,
            Clock clock,
            AppointmentNotificationService appointmentNotificationService,
            DoctorNotificationService doctorNotificationService,
            ZoneId appointmentZone) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.revisionRepository = revisionRepository;
        this.clock = clock;
        this.appointmentNotificationService = appointmentNotificationService;
        this.doctorNotificationService = doctorNotificationService;
        this.appointmentZone = appointmentZone;
    }

    @Transactional(readOnly = true)
    public ConsultationResponse findMine(String email, UUID appointmentId) {
        Appointment appointment = ownedAppointment(email, appointmentId);
        requireClinicalAppointment(appointment);
        Consultation consultation = consultationRepository.findByAppointmentId(appointment.getId())
                .orElseThrow(() -> new NotFoundException("Aucun compte rendu n'est enregistré pour ce rendez-vous."));
        return response(consultation);
    }

    @Transactional
    public ConsultationResponse save(String email, UUID appointmentId, ConsultationRequest request) {
        Appointment appointment = ownedAppointment(email, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED
                && appointment.getStatus() != AppointmentStatus.DONE) {
            throw new ConflictException("Le compte rendu est disponible pour un rendez-vous confirmé ou terminé.");
        }
        Instant now = clock.instant();
        Consultation consultation = consultationRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> consultationRepository.save(new Consultation(appointment, now)));
        if (consultation.isFinalized()) {
            throw new ConflictException("Ce compte rendu est finalisé et ne peut plus être modifié.");
        }
        consultation.update(request.report(), request.observations(), request.diagnosis(), now);
        if (request.finalized()) {
            if (request.report() == null || request.report().isBlank()) {
                throw new ConflictException("Un compte rendu est nécessaire pour finaliser la consultation.");
            }
            if (appointment.getScheduledAt().isAfter(
                    LocalDateTime.now(clock.withZone(appointmentZone)))) {
                throw new ConflictException("La consultation ne peut être finalisée avant l’horaire prévu.");
            }
            consultation.finalizeConsultation(now);
            if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
                appointment.setStatus(AppointmentStatus.DONE);
                appointmentNotificationService.cancelPendingNotifications(appointmentId);
                doctorNotificationService.appointmentChanged(appointment, false);
            }
        }

        consultation = consultationRepository.save(consultation);
        int revision = revisionRepository.countByConsultationId(consultation.getId()) + 1;
        revisionRepository.save(new ConsultationRevision(consultation, revision,
                consultation.getAppointment().getDoctor().getUser().getId(), now));
        return response(consultation);
    }

    private void requireClinicalAppointment(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED
                && appointment.getStatus() != AppointmentStatus.DONE) {
            throw new NotFoundException("Rendez-vous introuvable ou accès clinique non autorisé.");
        }
    }

    private Appointment ownedAppointment(String email, UUID appointmentId) {
        Doctor doctor = doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."));
        return appointmentRepository.findWithDoctorUserById(appointmentId)
                .filter(appointment -> appointment.getDoctor().getId().equals(doctor.getId()))
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
    }

    private ConsultationResponse response(Consultation consultation) {
        List<ConsultationRevisionResponse> revisions = revisionRepository
                .findAllByConsultationIdOrderByRevisionNumberDesc(consultation.getId()).stream()
                .map(revision -> new ConsultationRevisionResponse(
                        revision.getRevisionNumber(), revision.getReport(), revision.getObservations(),
                        revision.getDiagnosis(), revision.getActorUserId(), revision.getCreatedAt()))
                .toList();
        return new ConsultationResponse(
                consultation.getId(), consultation.getAppointment().getId(), consultation.getReport(),
                consultation.getObservations(), consultation.getDiagnosis(), consultation.isFinalized(),
                consultation.getCreatedAt(), consultation.getUpdatedAt(), revisions);
    }
}
