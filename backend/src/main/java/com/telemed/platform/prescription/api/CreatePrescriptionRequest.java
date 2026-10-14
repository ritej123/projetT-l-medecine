package com.telemed.platform.prescription.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePrescriptionRequest(@NotBlank @Size(max = 10000) String content) {
}
