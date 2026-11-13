package com.telemed.platform.doctor.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DoctorProfileRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Size(max = 2048) String profilePhotoUrl,
        @Size(max = 40) String professionalPhone,
        @Size(max = 500) String officeAddress,
        @Size(max = 120) String officeCity,
        @Size(max = 40) String professionalNumber,
        @Size(max = 2048) String documentUrl,
        boolean emailNotificationsEnabled) {
}