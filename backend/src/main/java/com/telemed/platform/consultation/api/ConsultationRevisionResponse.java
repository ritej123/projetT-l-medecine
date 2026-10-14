package com.telemed.platform.consultation.api;

import java.time.Instant;
import java.util.UUID;

public record ConsultationRevisionResponse(
        int revisionNumber, String report, String observations, String diagnosis, UUID actorUserId, Instant createdAt) {
}
