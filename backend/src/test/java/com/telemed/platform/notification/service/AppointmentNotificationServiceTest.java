package com.telemed.platform.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.notification.domain.AppointmentEmailNotification;
import com.telemed.platform.notification.domain.NotificationStatus;
import com.telemed.platform.notification.domain.NotificationType;
import com.telemed.platform.notification.repository.AppointmentEmailNotificationRepository;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZONE);

    @Mock
    private AppointmentEmailNotificationRepository notificationRepository;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private AppointmentNotificationService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentNotificationService(
                notificationRepository, mailSenderProvider, CLOCK, ZONE, "no-reply@example.test");
    }

    @Test
    void queuesConfirmationAndOneHourReminderInTheSameAppointmentFlow() {
        Appointment appointment = appointment(LocalDateTime.of(2026, 10, 13, 12, 0));
        when(notificationRepository.save(any(AppointmentEmailNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<AppointmentEmailNotification> captor =
                ArgumentCaptor.forClass(AppointmentEmailNotification.class);

        service.enqueueForBooking(appointment, "patient@example.test");

        verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AppointmentEmailNotification::getType)
                .containsExactly(
                        NotificationType.APPOINTMENT_CONFIRMATION,
                        NotificationType.APPOINTMENT_REMINDER);
        assertThat(captor.getAllValues()).allMatch(
                notification -> notification.getRecipient().equals("patient@example.test"));
    }

    @Test
    void doesNotScheduleAnAlreadyDueReminder() {
        when(notificationRepository.save(any(AppointmentEmailNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.enqueueForBooking(appointment(LocalDateTime.of(2026, 10, 12, 10, 30)), "patient@example.test");

        verify(notificationRepository).save(any(AppointmentEmailNotification.class));
    }

    @Test
    void sendsOnlyGenericAppointmentDetailsByEmail() {
        AppointmentEmailNotification notification = new AppointmentEmailNotification(
                UUID.randomUUID(),
                "patient@example.test",
                NotificationType.APPOINTMENT_CONFIRMATION,
                CLOCK.instant(),
                LocalDateTime.of(2026, 10, 13, 12, 0));
        when(notificationRepository.findDueForUpdate(
                        any(), any(), any(Pageable.class)))
                .thenReturn(List.of(notification));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);

        service.deliverDueNotifications();

        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("patient@example.test");
        assertThat(message.getValue().getText()).contains("13 octobre");
        assertThat(message.getValue().getText()).doesNotContain("dossier", "ordonnance", "diagnostic");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void retriesEmailFailuresWithoutLoggingOrDisclosingMessageContents() {
        AppointmentEmailNotification notification = new AppointmentEmailNotification(
                UUID.randomUUID(),
                "patient@example.test",
                NotificationType.APPOINTMENT_REMINDER,
                CLOCK.instant(),
                LocalDateTime.of(2026, 10, 13, 12, 0));
        when(notificationRepository.findDueForUpdate(
                        any(), any(), any(Pageable.class)))
                .thenReturn(List.of(notification));
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        service.deliverDueNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt()).isAfter(CLOCK.instant());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    private Appointment appointment(LocalDateTime scheduledAt) {
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null),
                scheduledAt,
                30);
        ReflectionTestUtils.setField(appointment, "id", UUID.randomUUID());
        return appointment;
    }
}
