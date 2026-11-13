package com.telemed.platform.user.api;

import jakarta.validation.constraints.Size;

public record RejectDoctorRequest(
        @Size(max = 1000) String reason) {
}