package com.telemed.platform.doctor.api;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.availability.api.AvailabilityResponse;

public record PatientDoctorDetailResponse(
        UUID id,
        String firstName,
        String lastName,
        String specialty,
        String officeAddress,
        String officeCity,
        String profilePhotoUrl,
        String bio,
        List<AvailabilityResponse> availabilities) {
}
