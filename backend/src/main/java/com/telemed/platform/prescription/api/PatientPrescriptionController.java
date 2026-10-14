package com.telemed.platform.prescription.api;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.prescription.service.PrescriptionService;
import com.telemed.platform.prescription.service.PrescriptionPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/patient/prescriptions")
@PreAuthorize("hasRole('PATIENT')")
public class PatientPrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PrescriptionPdfService pdfService;

    public PatientPrescriptionController(
            PrescriptionService prescriptionService,
            PrescriptionPdfService pdfService) {
        this.prescriptionService = prescriptionService;
        this.pdfService = pdfService;
    }

    @GetMapping
    public List<PrescriptionResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return prescriptionService.findPatientPrescriptions(jwt.getClaimAsString("email"));
    }

    @GetMapping("/{prescriptionId}/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID prescriptionId) {
        byte[] pdf = pdfService.createSignedPdf(
                prescriptionService.findFinalForPatient(jwt.getClaimAsString("email"), prescriptionId));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ordonnance-" + prescriptionId + ".pdf\"")
                .body(pdf);
    }
}
