package com.telemed.platform.prescription.api;

import java.util.UUID;

import com.telemed.platform.prescription.service.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.telemed.platform.prescription.service.PrescriptionDraftService;
import com.telemed.platform.prescription.service.PrescriptionPdfService;

@RestController
@RequestMapping("/api/v1/doctor")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorPrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PrescriptionDraftService draftService;
    private final PrescriptionPdfService pdfService;

    public DoctorPrescriptionController(
            PrescriptionService prescriptionService,
            PrescriptionDraftService draftService,
            PrescriptionPdfService pdfService) {
        this.prescriptionService = prescriptionService;
        this.draftService = draftService;
        this.pdfService = pdfService;
    }

    @PostMapping("/appointments/{appointmentId}/prescription/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionResponse generateDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody GeneratePrescriptionDraftRequest request) {
        return draftService.generate(
                jwt.getClaimAsString("email"), appointmentId, request.consultationNotes());
    }

    @PostMapping("/appointments/{appointmentId}/prescription")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionResponse createDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        return prescriptionService.createDraft(jwt.getClaimAsString("email"), appointmentId, request.content());
    }

    @GetMapping("/appointments/{appointmentId}/prescription")
    public PrescriptionResponse findForAppointment(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID appointmentId) {
        return prescriptionService.findForDoctorAppointment(jwt.getClaimAsString("email"), appointmentId);
    }

    @PatchMapping("/prescriptions/{prescriptionId}/validate")
    public PrescriptionResponse validate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID prescriptionId) {
        return prescriptionService.validate(jwt.getClaimAsString("email"), prescriptionId);
    }

    @PutMapping("/prescriptions/{prescriptionId}")
    public PrescriptionResponse updateDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID prescriptionId,
            @Valid @RequestBody UpdatePrescriptionRequest request) {
        return prescriptionService.updateDraft(jwt.getClaimAsString("email"), prescriptionId, request.content());
    }

    @GetMapping("/prescriptions/{prescriptionId}/history")
    public java.util.List<PrescriptionRevisionResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID prescriptionId) {
        return prescriptionService.history(jwt.getClaimAsString("email"), prescriptionId);
    }

    @GetMapping("/prescriptions/{prescriptionId}/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID prescriptionId) {
        byte[] pdf = pdfService.createSignedPdf(
                prescriptionService.findFinalForDoctor(jwt.getClaimAsString("email"), prescriptionId));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ordonnance-" + prescriptionId + ".pdf\"")
                .body(pdf);
    }
}
