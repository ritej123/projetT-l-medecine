package com.telemed.platform.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.notification.domain.AppointmentEmailNotification;
import com.telemed.platform.notification.domain.NotificationType;
import com.telemed.platform.notification.repository.AppointmentEmailNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorStatusNotificationService {

    private final AppointmentEmailNotificationRepository notificationRepository;
    private final Clock clock;

    public DoctorStatusNotificationService(
            AppointmentEmailNotificationRepository notificationRepository,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public void enqueueApproved(Doctor doctor) {
        enqueue(doctor, NotificationType.DOCTOR_APPROVED,
                "Votre compte médecin a été validé. Vous pouvez maintenant vous connecter à votre espace.");
    }

    @Transactional
    public void enqueueRejected(Doctor doctor, String reason) {
        String body = "Votre demande de compte médecin a été refusée.";
        if (reason != null && !reason.isBlank()) {
            body += "\nMotif : " + reason.trim();
        }
        enqueue(doctor, NotificationType.DOCTOR_REJECTED, body);
    }

    @Transactional
    public void enqueueSuspended(Doctor doctor) {
        enqueue(doctor, NotificationType.DOCTOR_SUSPENDED,
                "Votre compte médecin a été suspendu. Contactez l'administration.");
    }

    @Transactional
    public void enqueueReactivated(Doctor doctor) {
        enqueue(doctor, NotificationType.DOCTOR_REACTIVATED,
                "Votre compte médecin a été réactivé. Vous pouvez à nouveau vous connecter.");
    }

    private void enqueue(Doctor doctor, NotificationType type, String body) {
        notificationRepository.save(new AppointmentEmailNotification(
                doctor.getUser().getEmail(),
                type,
                clock.instant(),
                null,
                (int) (System.currentTimeMillis() % 1000000000),
                body));
    }
}