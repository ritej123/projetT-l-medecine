package com.telemed.platform.user.api;

import java.time.Instant;
import java.util.UUID;

public record AdminPatientResponse(UUID id, String email, boolean enabled, Instant createdAt) {
}
