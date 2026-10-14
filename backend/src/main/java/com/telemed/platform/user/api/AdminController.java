package com.telemed.platform.user.api;

import java.time.LocalDate;
import java.util.UUID;

import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.user.service.AdminDashboardService;
import com.telemed.platform.user.service.AdminManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final AdminManagementService managementService;

    public AdminController(AdminDashboardService dashboardService, AdminManagementService managementService) {
        this.dashboardService = dashboardService;
        this.managementService = managementService;
    }

    @GetMapping("/dashboard/stats")
    public AdminDashboardStats dashboardStats() {
        return dashboardService.getStats();
    }

    @GetMapping("/doctors")
    public AdminPageResponse<AdminDoctorResponse> doctors(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DoctorVerificationStatus status,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return AdminPageResponse.from(managementService.doctors(search, status, enabled, from, to, page, size));
    }

    @GetMapping("/patients")
    public AdminPageResponse<AdminPatientResponse> patients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return AdminPageResponse.from(managementService.patients(search, enabled, from, to, page, size));
    }

    @GetMapping("/appointments")
    public AdminPageResponse<com.telemed.platform.appointment.api.AdminAppointmentResponse> appointments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return AdminPageResponse.from(managementService.appointments(search, status, from, to, page, size));
    }

    @GetMapping("/audit-logs")
    public AdminPageResponse<AdminAuditLogResponse> auditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return AdminPageResponse.from(managementService.auditLogs(page, size));
    }

    @PatchMapping("/doctors/{doctorId}/verify")
    public AdminDoctorResponse verifyDoctor(
            @PathVariable UUID doctorId, @AuthenticationPrincipal Jwt jwt) {
        return managementService.verifyDoctor(doctorId, jwt.getClaimAsString("email"));
    }

    @PatchMapping("/doctors/{doctorId}/reject")
    public AdminDoctorResponse rejectDoctor(
            @PathVariable UUID doctorId, @AuthenticationPrincipal Jwt jwt) {
        return managementService.rejectDoctor(doctorId, jwt.getClaimAsString("email"));
    }

    @PatchMapping("/doctors/{doctorId}/status")
    public AdminDoctorResponse updateDoctorStatus(
            @PathVariable UUID doctorId,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return managementService.setDoctorStatus(doctorId, request.enabled(), jwt.getClaimAsString("email"));
    }

    @PatchMapping("/patients/{patientId}/status")
    public AdminPatientResponse updatePatientStatus(
            @PathVariable UUID patientId,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return managementService.setPatientStatus(patientId, request.enabled(), jwt.getClaimAsString("email"));
    }
}
