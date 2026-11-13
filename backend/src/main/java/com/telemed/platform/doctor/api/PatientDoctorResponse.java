package com.telemed.platform.doctor.api;

import java.util.UUID;

public record PatientDoctorResponse(
        UUID id,
        String firstName,
        String lastName,
        String specialty,
        String officeAddress,
        String officeCity,
        String professionalNumber,
        String profilePhotoUrl) {
}