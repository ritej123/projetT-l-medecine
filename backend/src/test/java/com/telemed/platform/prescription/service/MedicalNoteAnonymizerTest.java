package com.telemed.platform.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MedicalNoteAnonymizerTest {

    private final MedicalNoteAnonymizer anonymizer = new MedicalNoteAnonymizer();

    @Test
    void removesCommonPatientIdentifiersBeforeLlmSubmission() {
        String anonymized = anonymizer.anonymize(
                "Patiente : Jeanne Dupont, née le 12/03/1980, téléphone 06 12 34 56 78, email jeanne@example.test. "
                        + "Mme Martin consulte pour une toux.");

        assertThat(anonymized)
                .doesNotContain("Jeanne Dupont")
                .doesNotContain("12/03/1980")
                .doesNotContain("06 12 34 56 78")
                .doesNotContain("jeanne@example.test")
                .doesNotContain("Mme Martin")
                .contains("toux");
    }
}
