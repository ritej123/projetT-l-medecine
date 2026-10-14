package com.telemed.platform.doctor.api;

import java.util.UUID;

public record DoctorRegistrationResponse(UUID id, String email, String specialty, String bio) {
}
