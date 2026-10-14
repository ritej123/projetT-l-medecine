package com.telemed.platform.availability.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.availability.service.AvailabilityExceptionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor/availability-exceptions")
@PreAuthorize("hasRole('DOCTOR')")
public class AvailabilityExceptionController {

    private final AvailabilityExceptionService exceptionService;

    public AvailabilityExceptionController(AvailabilityExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @GetMapping
    public List<AvailabilityExceptionResponse> findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return exceptionService.findMine(jwt.getClaimAsString("email"), from, to);
    }

    @PostMapping
    public AvailabilityExceptionResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AvailabilityExceptionRequest request) {
        return exceptionService.create(jwt.getClaimAsString("email"), request);
    }

    @DeleteMapping("/{exceptionId}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exceptionId) {
        exceptionService.delete(jwt.getClaimAsString("email"), exceptionId);
    }
}
