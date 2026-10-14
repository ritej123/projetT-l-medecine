package com.telemed.platform.prescription.api;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import com.telemed.platform.prescription.domain.PrescriptionStatus;

public record PrescriptionResponse(
        UUID id,
        UUID appointmentId,
        String content,
        PrescriptionStatus status,
        LocalDate date,
        UUID signedByUserId,
        Instant signedAt,
        String contentSha256,
        String digitalSignature) {
}
