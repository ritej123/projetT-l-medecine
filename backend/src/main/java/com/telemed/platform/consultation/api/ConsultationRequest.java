package com.telemed.platform.consultation.api;

import jakarta.validation.constraints.Size;

public record ConsultationRequest(
        @Size(max = 20000) String report,
        @Size(max = 20000) String observations,
        @Size(max = 10000) String diagnosis,
        boolean finalized) {
}
