package com.telemed.platform.teleconsultation.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record TeleconsultationRoomEvent(
        UUID appointmentId,
        String senderId,
        TeleconsultationSignalType type,
        JsonNode payload,
        Instant sentAt) {
}
