package com.telemed.platform.patient.api;

import java.util.UUID;

import com.telemed.platform.patient.service.DoctorPatientService;
import com.telemed.platform.user.api.AdminPageResponse;
import org.springframework.data.domain.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/doctor/patients")
@PreAuthorize("hasRole('DOCTOR')")
@Validated
public class DoctorPatientController {

    private final DoctorPatientService patientService;

    public DoctorPatientController(DoctorPatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    public AdminPageResponse<DoctorPatientResponse> findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return patientService.findMine(jwt.getClaimAsString("email"), search, PageRequest.of(page, size));
    }

    @GetMapping("/{patientId}")
    public DoctorPatientDetailResponse findPatient(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID patientId) {
        return patientService.findPatient(jwt.getClaimAsString("email"), patientId);
    }
}
