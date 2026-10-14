package com.telemed.platform.patient.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.domain.AppointmentStatus;

public record DoctorPatientDetailResponse(
        UUID patientId,
        String email,
        String medicalRecord,
        List<AppointmentHistory> appointmentHistory,
        List<ConsultationHistory> consultationHistory) {

    public record AppointmentHistory(
            UUID appointmentId, LocalDateTime scheduledAt, int durationMinutes, AppointmentStatus status) {
    }

    public record ConsultationHistory(
            UUID appointmentId, LocalDateTime scheduledAt, String report, String observations, String diagnosis) {
    }
}
