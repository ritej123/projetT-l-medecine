package com.telemed.platform.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.notification.api.DoctorNotificationResponse;
import com.telemed.platform.notification.domain.DoctorNotification;
import com.telemed.platform.notification.domain.DoctorNotificationType;
import com.telemed.platform.notification.repository.DoctorNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class DoctorNotificationService {

    private final DoctorRepository doctorRepository;
    private final DoctorNotificationRepository notificationRepository;
    private final Clock clock;
    private final AppointmentRepository appointmentRepository;
    private final ZoneId appointmentZone;

    public DoctorNotificationService(
            DoctorRepository doctorRepository,
            DoctorNotificationRepository notificationRepository,
            AppointmentRepository appointmentRepository,
            Clock clock,
            ZoneId appointmentZone) {
        this.doctorRepository = doctorRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
        this.appointmentRepository = appointmentRepository;
        this.appointmentZone = appointmentZone;
    }

    @Transactional
    public void appointmentCreated(Appointment appointment) {
        create(appointment, DoctorNotificationType.NEW_APPOINTMENT,
                "Un nouveau rendez-vous est en attente de confirmation.");
    }

    @Transactional
    public void appointmentChanged(Appointment appointment, boolean cancelled) {
        create(appointment,
                cancelled ? DoctorNotificationType.APPOINTMENT_CANCELLED : DoctorNotificationType.APPOINTMENT_UPDATED,
                cancelled ? "Un rendez-vous a été annulé." : "Le statut d’un rendez-vous a été modifié.");
    }

    @Scheduled(fixedDelayString = "${app.notification.doctor-reminder-interval-ms:60000}")
    @Transactional
    public void createUpcomingReminders() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));
        List<Appointment> due = appointmentRepository
                .findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                        now.plusMinutes(55), now.plusMinutes(65),
                        List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
        due.forEach(appointment -> {
            if (!notificationRepository.existsByAppointmentIdAndType(
                    appointment.getId(), DoctorNotificationType.APPOINTMENT_REMINDER)) {
                create(appointment, DoctorNotificationType.APPOINTMENT_REMINDER,
                        "Un rendez-vous est prévu dans environ une heure.");
            }
        });
    }

    @Transactional(readOnly = true)
    public List<DoctorNotificationResponse> findMine(String email) {
        UUID userId = findDoctorUserId(email);
        return notificationRepository.findAllByDoctorUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100)).stream()
                .map(DoctorNotificationService::toResponse)
                .toList();
    }

    @Transactional
    public DoctorNotificationResponse markRead(String email, UUID notificationId) {
        DoctorNotification notification = notificationRepository
                .findByIdAndDoctorUserId(notificationId, findDoctorUserId(email))
                .orElseThrow(() -> new NotFoundException("Notification introuvable."));
        notification.markRead(clock.instant());
        return toResponse(notification);
    }

    private void create(Appointment appointment, DoctorNotificationType type, String message) {
        notificationRepository.save(new DoctorNotification(
                appointment.getDoctor().getUser().getId(), appointment.getId(), type, message, clock.instant()));
    }

    private UUID findDoctorUserId(String email) {
        return doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."))
                .getUser().getId();
    }

    private static DoctorNotificationResponse toResponse(DoctorNotification notification) {
        return new DoctorNotificationResponse(
                notification.getId(), notification.getAppointmentId(), notification.getType(),
                notification.getMessage(), notification.getCreatedAt(), notification.getReadAt());
    }
}
