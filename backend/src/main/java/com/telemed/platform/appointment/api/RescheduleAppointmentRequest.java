package com.telemed.platform.appointment.api;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record RescheduleAppointmentRequest(@NotNull LocalDateTime scheduledAt) {
}
