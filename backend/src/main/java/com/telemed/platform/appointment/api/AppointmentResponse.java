package com.telemed.platform.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.telemed.platform.appointment.domain.AppointmentStatus;

public record AppointmentResponse(
        UUID id, UUID patientId, UUID doctorId, LocalDateTime scheduledAt, int durationMinutes, AppointmentStatus status) {
}
