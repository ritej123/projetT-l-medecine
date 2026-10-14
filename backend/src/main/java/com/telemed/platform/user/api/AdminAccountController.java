package com.telemed.platform.user.api;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.doctor.api.DoctorRegistrationResponse;
import com.telemed.platform.doctor.api.DoctorResponse;
import com.telemed.platform.patient.api.PatientResponse;
import com.telemed.platform.user.service.AccountProvisioningService;
import com.telemed.platform.user.service.AdminManagementService;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AccountProvisioningService accountProvisioningService;
    private final AdminManagementService adminManagementService;

    public AdminAccountController(
            AccountProvisioningService accountProvisioningService,
            AdminManagementService adminManagementService) {
        this.accountProvisioningService = accountProvisioningService;
        this.adminManagementService = adminManagementService;
    }

    @PostMapping("/doctors")
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorResponse createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        return accountProvisioningService.createDoctor(request);
    }

    @GetMapping("/doctors/pending")
    public List<DoctorRegistrationResponse> findPendingDoctorApplications() {
        return adminManagementService.doctors(
                        null, DoctorVerificationStatus.PENDING, null, null, null, 0, 100)
                .getContent()
                .stream()
                .map(doctor -> new DoctorRegistrationResponse(
                        doctor.id(), doctor.email(), doctor.specialty(), doctor.bio()))
                .toList();
    }

    @PatchMapping("/doctors/{doctorId}/approve")
    public AdminDoctorResponse approveDoctorApplication(
            @PathVariable UUID doctorId, @AuthenticationPrincipal Jwt jwt) {
        return adminManagementService.verifyDoctor(doctorId, jwt.getClaimAsString("email"));
    }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse createPatient(@Valid @RequestBody CreatePatientRequest request) {
        return accountProvisioningService.createPatient(request);
    }
}
