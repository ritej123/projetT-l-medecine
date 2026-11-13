package com.telemed.platform.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDoctorRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 120) String specialty,
        @Size(max = 4000) String bio,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Size(max = 500) String officeAddress,
        @Size(max = 120) String officeCity,
        @Size(max = 40) String professionalNumber,
        @Size(max = 40) String professionalPhone,
        @Size(max = 2048) String documentUrl) {
}
