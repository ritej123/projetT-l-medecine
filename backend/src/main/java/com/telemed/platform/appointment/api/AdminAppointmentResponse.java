package com.telemed.platform.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.telemed.platform.appointment.domain.AppointmentStatus;

public record AdminAppointmentResponse(
        UUID id,
        String patientEmail,
        String doctorEmail,
        String specialty,
        LocalDateTime scheduledAt,
        int durationMinutes,
        AppointmentStatus status) {
}
