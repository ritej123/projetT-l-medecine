package com.telemed.platform.appointment.api;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.service.AppointmentService;
import com.telemed.platform.appointment.api.RescheduleAppointmentRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor/appointments")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorAppointmentController {

    private final AppointmentService appointmentService;

    public DoctorAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<AppointmentResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return appointmentService.findDoctorAppointments(jwt.getClaimAsString("email"));
    }

    @PatchMapping("/{appointmentId}/status")
    public AppointmentResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.updateStatus(jwt.getClaimAsString("email"), appointmentId, request.status());
    }

    @PatchMapping("/{appointmentId}/reschedule")
    public AppointmentResponse reschedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody RescheduleAppointmentRequest request) {
        return appointmentService.reschedule(jwt.getClaimAsString("email"), appointmentId, request);
    }
}
