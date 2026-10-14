package com.telemed.platform.availability.api;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.availability.service.AvailabilityService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor/availabilities")
@PreAuthorize("hasRole('DOCTOR')")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<AvailabilityResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return availabilityService.findMine(jwt.getClaimAsString("email"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAvailabilityRequest request) {
        return availabilityService.create(jwt.getClaimAsString("email"), request);
    }

    @PutMapping("/{availabilityId}")
    public AvailabilityResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID availabilityId,
            @Valid @RequestBody CreateAvailabilityRequest request) {
        return availabilityService.update(jwt.getClaimAsString("email"), availabilityId, request);
    }

    @DeleteMapping("/{availabilityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID availabilityId) {
        availabilityService.delete(jwt.getClaimAsString("email"), availabilityId);
    }
}
