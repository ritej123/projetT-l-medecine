package com.telemed.platform.appointment.api;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import com.telemed.platform.appointment.service.AppointmentService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/patient/appointments")
@PreAuthorize("hasRole('PATIENT')")
@Validated
public class PatientAppointmentController {

    private final AppointmentService appointmentService;

    public PatientAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public AppointmentResponse book(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentService.book(jwt.getClaimAsString("email"), request);
    }

    @GetMapping
    public List<AppointmentResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return appointmentService.findPatientAppointments(jwt.getClaimAsString("email"));
    }

    @GetMapping("/doctors/{doctorId}/slots")
    public List<AvailableAppointmentSlotResponse> findAvailableSlots(
            @PathVariable UUID doctorId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointmentService.findAvailableSlots(doctorId, from, to);
    }

    @PatchMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId) {
        return appointmentService.cancelByPatient(jwt.getClaimAsString("email"), appointmentId);
    }
}
