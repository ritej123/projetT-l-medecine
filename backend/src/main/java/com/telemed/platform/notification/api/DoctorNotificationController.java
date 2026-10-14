package com.telemed.platform.notification.api;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.notification.service.DoctorNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor/notifications")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorNotificationController {

    private final DoctorNotificationService notificationService;

    public DoctorNotificationController(DoctorNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<DoctorNotificationResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.findMine(jwt.getClaimAsString("email"));
    }

    @PatchMapping("/{notificationId}/read")
    public DoctorNotificationResponse markRead(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
        return notificationService.markRead(jwt.getClaimAsString("email"), notificationId);
    }
}
