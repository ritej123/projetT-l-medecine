package com.telemed.platform.user.api;

import java.time.Instant;
import java.util.UUID;

import com.telemed.platform.user.domain.AdminAuditResult;

public record AdminAuditLogResponse(
        UUID id,
        String actorEmail,
        String action,
        String targetType,
        UUID targetId,
        AdminAuditResult result,
        Instant createdAt) {
}
