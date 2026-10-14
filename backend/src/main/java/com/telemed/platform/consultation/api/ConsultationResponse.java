package com.telemed.platform.consultation.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConsultationResponse(
        UUID id,
        UUID appointmentId,
        String report,
        String observations,
        String diagnosis,
        boolean finalized,
        Instant createdAt,
        Instant updatedAt,
        List<ConsultationRevisionResponse> revisions) {
}
