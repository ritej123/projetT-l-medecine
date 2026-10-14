package com.telemed.platform.appointment.api;

import com.telemed.platform.appointment.service.DoctorDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor/dashboard")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorDashboardController {

    private final DoctorDashboardService dashboardService;

    public DoctorDashboardController(DoctorDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public DoctorDashboardStatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.findMine(jwt.getClaimAsString("email"));
    }
}
