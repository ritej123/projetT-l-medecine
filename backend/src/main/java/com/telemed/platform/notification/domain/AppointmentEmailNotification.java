package com.telemed.platform.notification.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "appointment_email_notifications")
public class AppointmentEmailNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "appointment_at", nullable = false)
    private LocalDateTime appointmentAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "event_sequence", nullable = false)
    private int eventSequence;

    @Version
    @Column(nullable = false)
    private long version;

    protected AppointmentEmailNotification() {
    }

    public AppointmentEmailNotification(
            UUID appointmentId,
            String recipient,
            NotificationType type,
            Instant scheduledAt,
            LocalDateTime appointmentAt) {
        this(appointmentId, recipient, type, scheduledAt, appointmentAt, 0);
    }

    public AppointmentEmailNotification(
            UUID appointmentId,
            String recipient,
            NotificationType type,
            Instant scheduledAt,
            LocalDateTime appointmentAt,
            int eventSequence) {
        this.appointmentId = appointmentId;
        this.recipient = recipient;
        this.type = type;
        this.scheduledAt = scheduledAt;
        this.nextAttemptAt = scheduledAt;
        this.appointmentAt = appointmentAt;
        this.eventSequence = eventSequence;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public int getAttempts() {
        return attempts;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public LocalDateTime getAppointmentAt() {
        return appointmentAt;
    }

    public void markSent() {
        status = NotificationStatus.SENT;
        attempts++;
    }

    public void markFailedPermanently() {
        status = NotificationStatus.FAILED;
        attempts++;
    }

    public void cancel() {
        status = NotificationStatus.CANCELLED;
    }

    public void retryAt(Instant nextAttemptAt) {
        status = NotificationStatus.PENDING;
        attempts++;
        this.nextAttemptAt = nextAttemptAt;
    }
}
