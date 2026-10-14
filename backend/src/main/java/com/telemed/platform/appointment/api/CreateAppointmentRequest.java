package com.telemed.platform.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateAppointmentRequest(@NotNull UUID doctorId, @NotNull LocalDateTime scheduledAt) {
}
