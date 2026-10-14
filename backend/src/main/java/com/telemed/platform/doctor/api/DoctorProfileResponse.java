package com.telemed.platform.doctor.api;

import java.util.UUID;

import com.telemed.platform.doctor.domain.DoctorVerificationStatus;

public record DoctorProfileResponse(
        UUID doctorId,
        String email,
        String firstName,
        String lastName,
        String profilePhotoUrl,
        String specialty,
        String bio,
        String professionalPhone,
        String officeAddress,
        String professionalNumber,
        boolean professionalNumberVerified,
        boolean emailNotificationsEnabled,
        DoctorVerificationStatus verificationStatus) {
}
