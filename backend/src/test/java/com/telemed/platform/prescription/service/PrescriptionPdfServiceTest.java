package com.telemed.platform.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.prescription.domain.Prescription;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PrescriptionPdfServiceTest {

    @Test
    void exportsValidatedPrescriptionWithoutSignatureDetails() throws Exception {
        String content = "Amoxicilline 500 mg, trois fois par jour. Suivi médical.";
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null),
                LocalDateTime.of(2026, 10, 12, 12, 0),
                30);
        Prescription prescription = new Prescription(appointment, content);
        prescription.validateOn(LocalDate.of(2026, 10, 12));

        byte[] pdf = new PrescriptionPdfService().createPdf(prescription);

        assertThat(pdf).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(pdf)) {
            String extractedText = new PDFTextStripper().getText(document);
            assertThat(extractedText).contains("ORDONNANCE MEDICALE");
            assertThat(extractedText).contains("Amoxicilline 500 mg");
            assertThat(extractedText).doesNotContain("Signature", "SHA-256", "HMAC");
        }
    }
}
