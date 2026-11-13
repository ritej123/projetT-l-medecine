package com.telemed.platform.user.api;

import java.time.Instant;
import java.util.UUID;

import com.telemed.platform.doctor.domain.DoctorVerificationStatus;

public record AdminDoctorDetailResponse(
        UUID id,
        String email,
        String specialty,
        String bio,
        String firstName,
        String lastName,
        String professionalPhone,
        String officeAddress,
        String officeCity,
        String professionalNumber,
        boolean professionalNumberVerified,
        String documentUrl,
        Instant documentUploadedAt,
        DoctorVerificationStatus verificationStatus,
        boolean enabled,
        Instant createdAt) {
}
