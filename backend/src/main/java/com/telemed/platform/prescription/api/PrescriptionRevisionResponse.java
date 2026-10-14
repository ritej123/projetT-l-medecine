package com.telemed.platform.prescription.api;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionRevisionResponse(
        UUID id,
        int revisionNumber,
        String content,
        UUID actorUserId,
        String changeType,
        Instant createdAt) {
}
