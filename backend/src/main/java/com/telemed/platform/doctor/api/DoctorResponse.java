package com.telemed.platform.doctor.api;

import java.util.UUID;

public record DoctorResponse(UUID id, String specialty, String bio) {
}
