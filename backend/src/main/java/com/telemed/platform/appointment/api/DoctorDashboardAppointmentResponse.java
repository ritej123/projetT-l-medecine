package com.telemed.platform.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.telemed.platform.appointment.domain.AppointmentStatus;

public record DoctorDashboardAppointmentResponse(
        UUID id,
        UUID patientId,
        String patientEmail,
        LocalDateTime scheduledAt,
        int durationMinutes,
        AppointmentStatus status,
        String consultationReason) {

    public DoctorDashboardAppointmentResponse(
            UUID id,
            UUID patientId,
            String patientEmail,
            LocalDateTime scheduledAt,
            int durationMinutes,
            AppointmentStatus status) {
        this(id, patientId, patientEmail, scheduledAt, durationMinutes, status, null);
    }
}
