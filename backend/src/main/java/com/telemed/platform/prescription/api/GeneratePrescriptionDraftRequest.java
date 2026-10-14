package com.telemed.platform.prescription.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeneratePrescriptionDraftRequest(@NotBlank @Size(max = 10000) String consultationNotes) {
}
