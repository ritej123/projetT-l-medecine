package com.telemed.platform.practitioner.api;

import java.util.UUID;

public record PractitionerResponse(UUID id, String displayName, String specialty) {
}
