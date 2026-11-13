package com.telemed.platform.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAppointmentRequest(
        @NotNull UUID doctorId,
        @NotNull LocalDateTime scheduledAt,
        @Size(max = 500) String consultationReason) {

    public CreateAppointmentRequest(UUID doctorId, LocalDateTime scheduledAt) {
        this(doctorId, scheduledAt, null);
    }
}
