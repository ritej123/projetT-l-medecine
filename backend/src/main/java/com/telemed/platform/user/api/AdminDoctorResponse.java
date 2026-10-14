package com.telemed.platform.user.api;

import java.time.Instant;
import java.util.UUID;

import com.telemed.platform.doctor.domain.DoctorVerificationStatus;

public record AdminDoctorResponse(
        UUID id,
        String email,
        String specialty,
        String bio,
        DoctorVerificationStatus verificationStatus,
        boolean enabled,
        Instant createdAt) {
}
