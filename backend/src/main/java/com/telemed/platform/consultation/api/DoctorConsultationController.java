package com.telemed.platform.consultation.api;

import java.util.UUID;

import com.telemed.platform.consultation.service.ConsultationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor/appointments/{appointmentId}/consultation")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorConsultationController {

    private final ConsultationService consultationService;

    public DoctorConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @GetMapping
    public ConsultationResponse findMine(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID appointmentId) {
        return consultationService.findMine(jwt.getClaimAsString("email"), appointmentId);
    }

    @PutMapping
    public ConsultationResponse save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody ConsultationRequest request) {
        return consultationService.save(jwt.getClaimAsString("email"), appointmentId, request);
    }
}
