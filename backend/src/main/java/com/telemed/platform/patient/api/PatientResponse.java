package com.telemed.platform.patient.api;

import java.util.UUID;

public record PatientResponse(UUID id, UUID userId) {
}
