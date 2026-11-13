package com.telemed.platform.prescription.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PatientPrescriptionResponse(
        UUID id,
        UUID appointmentId,
        UUID doctorId,
        String doctorName,
        String doctorSpecialty,
        String content,
        LocalDate date,
        LocalDateTime consultationAt) {
}
