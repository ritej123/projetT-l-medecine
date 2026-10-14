package com.telemed.platform.doctor.api;

import com.telemed.platform.doctor.service.DoctorProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor/profile")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorProfileController {

    private final DoctorProfileService profileService;

    public DoctorProfileController(DoctorProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public DoctorProfileResponse findMine(@AuthenticationPrincipal Jwt jwt) {
        return profileService.findMine(jwt.getClaimAsString("email"));
    }

    @PutMapping
    public DoctorProfileResponse updateMine(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DoctorProfileRequest request) {
        return profileService.updateMine(jwt.getClaimAsString("email"), request);
    }
}
