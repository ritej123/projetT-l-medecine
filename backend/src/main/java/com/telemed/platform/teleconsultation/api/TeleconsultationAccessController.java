package com.telemed.platform.teleconsultation.api;

import java.util.UUID;

import com.telemed.platform.teleconsultation.service.TeleconsultationAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teleconsultations")
@PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
public class TeleconsultationAccessController {

    private final TeleconsultationAccessService accessService;

    public TeleconsultationAccessController(TeleconsultationAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/{appointmentId}/access")
    public ResponseEntity<Void> checkAccess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId) {
        accessService.assertCanJoin(appointmentId, jwt.getClaimAsString("email"));
        return ResponseEntity.noContent().build();
    }
}
