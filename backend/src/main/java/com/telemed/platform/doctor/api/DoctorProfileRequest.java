package com.telemed.platform.doctor.api;

import jakarta.validation.constraints.Size;

public record DoctorProfileRequest(
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 2048) String profilePhotoUrl,
        @Size(max = 40) String professionalPhone,
        @Size(max = 500) String officeAddress,
        @Size(max = 40) String professionalNumber,
        boolean emailNotificationsEnabled) {
}
