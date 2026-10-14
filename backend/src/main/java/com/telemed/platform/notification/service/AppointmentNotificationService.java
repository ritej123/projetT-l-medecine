package com.telemed.platform.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.notification.domain.AppointmentEmailNotification;
import com.telemed.platform.notification.domain.NotificationStatus;
import com.telemed.platform.notification.domain.NotificationType;
import com.telemed.platform.notification.repository.AppointmentEmailNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentNotificationService.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 8;

    private final AppointmentEmailNotificationRepository notificationRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Clock clock;
    private final ZoneId appointmentZone;
    private final String fromAddress;

    public AppointmentNotificationService(
            AppointmentEmailNotificationRepository notificationRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            Clock clock,
            ZoneId appointmentZone,
            @Value("${app.email.from:no-reply@example.test}") String fromAddress) {
        this.notificationRepository = notificationRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
        this.fromAddress = fromAddress;
    }

    @Transactional
    public void enqueueForBooking(Appointment appointment, String recipient) {
        Instant now = clock.instant();
        notificationRepository.save(new AppointmentEmailNotification(
                appointment.getId(),
                recipient,
                NotificationType.APPOINTMENT_CONFIRMATION,
                now,
                appointment.getScheduledAt()));

        Instant reminderAt = appointment.getScheduledAt().atZone(appointmentZone).toInstant().minus(Duration.ofHours(1));
        if (reminderAt.isAfter(now)) {
            notificationRepository.save(new AppointmentEmailNotification(
                    appointment.getId(),
                    recipient,
                    NotificationType.APPOINTMENT_REMINDER,
                    reminderAt,
                    appointment.getScheduledAt()));
        }
    }

    @Transactional
    public void enqueueForReschedule(Appointment appointment, String recipient) {
        cancelPendingNotifications(appointment.getId());
        int sequence = Math.toIntExact(notificationRepository.countByAppointmentId(appointment.getId()));
        Instant now = clock.instant();
        notificationRepository.save(new AppointmentEmailNotification(
                appointment.getId(),
                recipient,
                NotificationType.APPOINTMENT_RESCHEDULED,
                now,
                appointment.getScheduledAt(),
                sequence));
        Instant reminderAt = appointment.getScheduledAt().atZone(appointmentZone).toInstant().minus(Duration.ofHours(1));
        if (reminderAt.isAfter(now)) {
            notificationRepository.save(new AppointmentEmailNotification(
                    appointment.getId(),
                    recipient,
                    NotificationType.APPOINTMENT_REMINDER,
                    reminderAt,
                    appointment.getScheduledAt(),
                    sequence + 1));
        }
    }

    @Transactional
    public void enqueueDoctorNotice(Appointment appointment, String recipient) {
        int sequence = Math.toIntExact(notificationRepository.countByAppointmentId(appointment.getId()));
        notificationRepository.save(new AppointmentEmailNotification(
                appointment.getId(),
                recipient,
                NotificationType.DOCTOR_APPOINTMENT_NOTICE,
                clock.instant(),
                appointment.getScheduledAt(),
                sequence));
    }

    @Transactional
    public void cancelPendingNotifications(UUID appointmentId) {
        notificationRepository.findAllByAppointmentIdAndStatusAndTypeIn(
                        appointmentId,
                        NotificationStatus.PENDING,
                        List.of(
                                NotificationType.APPOINTMENT_CONFIRMATION,
                                NotificationType.APPOINTMENT_REMINDER,
                                NotificationType.APPOINTMENT_RESCHEDULED))
                .forEach(AppointmentEmailNotification::cancel);
    }

    @Scheduled(fixedDelayString = "${app.email.delivery-interval-ms:30000}")
    @Transactional
    public void deliverDueNotifications() {
        Instant now = clock.instant();
        List<AppointmentEmailNotification> dueNotifications = notificationRepository.findDueForUpdate(
                NotificationStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE));
        for (AppointmentEmailNotification notification : dueNotifications) {
            try {
                send(notification);
                notification.markSent();
            } catch (MailException | IllegalStateException exception) {
                if (notification.getAttempts() + 1 >= MAX_ATTEMPTS) {
                    notification.markFailedPermanently();
                    LOGGER.error("Appointment email delivery exhausted retries. notificationId={}",
                            notification.getId());
                } else {
                    notification.retryAt(now.plus(retryDelay(notification.getAttempts())));
                    LOGGER.warn("Appointment email delivery failed; retry scheduled. notificationId={}",
                            notification.getId());
                }
            }
        }
    }

    private void send(AppointmentEmailNotification notification) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("Email delivery is not configured.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(notification.getRecipient());
        message.setSubject(switch (notification.getType()) {
            case APPOINTMENT_REMINDER -> "Rappel de votre rendez-vous";
            case APPOINTMENT_RESCHEDULED -> "Votre rendez-vous a été replanifié";
            case DOCTOR_APPOINTMENT_NOTICE -> "Mise à jour de votre agenda ClairSanté";
            case APPOINTMENT_CONFIRMATION -> "Confirmation de votre rendez-vous";
        });
        String formattedTime = notification.getAppointmentAt()
                .atZone(appointmentZone)
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", java.util.Locale.FRENCH));
        message.setText(switch (notification.getType()) {
            case APPOINTMENT_REMINDER ->
                    "Bonjour,\n\nNous vous rappelons votre rendez-vous prévu " + formattedTime + ".\n\nÀ bientôt.";
            case APPOINTMENT_RESCHEDULED ->
                    "Bonjour,\n\nVotre rendez-vous a été replanifié au " + formattedTime + ".\n\nÀ bientôt.";
            case DOCTOR_APPOINTMENT_NOTICE ->
                    "Bonjour,\n\nUn événement a mis à jour votre agenda ClairSanté pour le "
                            + formattedTime + ". Consultez votre espace médecin.";
            case APPOINTMENT_CONFIRMATION ->
                    "Bonjour,\n\nVotre rendez-vous du " + formattedTime + " a bien été enregistré.\n\nÀ bientôt.";
        });
        mailSender.send(message);
    }

    private Duration retryDelay(int previousAttempts) {
        long seconds = Math.min(30L * (1L << Math.min(previousAttempts, 10)), Duration.ofHours(6).toSeconds());
        return Duration.ofSeconds(seconds);
    }
}
