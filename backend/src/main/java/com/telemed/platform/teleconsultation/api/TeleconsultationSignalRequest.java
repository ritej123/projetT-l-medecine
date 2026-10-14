package com.telemed.platform.teleconsultation.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record TeleconsultationSignalRequest(
        @NotNull TeleconsultationSignalType type,
        @NotNull JsonNode payload) {
}
