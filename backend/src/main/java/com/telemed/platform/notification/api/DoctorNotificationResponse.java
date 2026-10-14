package com.telemed.platform.notification.api;

import java.time.Instant;
import java.util.UUID;

import com.telemed.platform.notification.domain.DoctorNotificationType;

public record DoctorNotificationResponse(
        UUID id,
        UUID appointmentId,
        DoctorNotificationType type,
        String message,
        Instant createdAt,
        Instant readAt) {
}
