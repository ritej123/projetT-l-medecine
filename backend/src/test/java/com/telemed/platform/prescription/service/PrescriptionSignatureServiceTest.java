package com.telemed.platform.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PrescriptionSignatureServiceTest {

    private final PrescriptionSignatureService signatureService =
            new PrescriptionSignatureService("a-long-independent-signing-key-at-least-32-bytes");

    @Test
    void signsAndVerifiesTheExactPrescriptionContentAndSigner() {
        UUID prescriptionId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        String signature = signatureService.sign(prescriptionId, doctorId, "2026-10-12T10:00:00Z", "sha256");

        assertThat(signatureService.verify(
                prescriptionId, doctorId, "2026-10-12T10:00:00Z", "sha256", signature)).isTrue();
        assertThat(signatureService.verify(
                prescriptionId, doctorId, "2026-10-12T10:00:00Z", "changed", signature)).isFalse();
    }

    @Test
    void refusesToStartWithoutASufficientlyLongSecretOutsideDevelopment() {
        assertThatThrownBy(() -> new PrescriptionSignatureService(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRESCRIPTION_SIGNING_SECRET");
    }

    @Test
    void generatesAnEphemeralSecretOnlyForDevelopmentProfile() {
        MockEnvironment developmentEnvironment = new MockEnvironment();
        developmentEnvironment.setActiveProfiles("dev");
        PrescriptionSignatureService developmentService =
                new PrescriptionSignatureService("", developmentEnvironment);

        assertThat(developmentService.sign(UUID.randomUUID(), UUID.randomUUID(), "now", "hash"))
                .isNotBlank();
    }
}
