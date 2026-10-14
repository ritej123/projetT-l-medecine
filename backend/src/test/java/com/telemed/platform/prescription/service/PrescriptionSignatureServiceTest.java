package com.telemed.platform.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

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
    void refusesToSignWithoutASufficientlyLongSecret() {
        PrescriptionSignatureService unconfigured = new PrescriptionSignatureService("");

        assertThatThrownBy(() -> unconfigured.sign(UUID.randomUUID(), UUID.randomUUID(), "now", "hash"))
                .isInstanceOf(com.telemed.platform.common.exception.ServiceUnavailableException.class);
    }
}
