package com.telemed.platform.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @JsonProperty("captcha_id") @Size(max = 36) String captchaId,
        @JsonProperty("captcha_text") @Size(max = 6) String captchaText) {

    public LoginRequest(String email, String password) {
        this(email, password, null, null);
    }
}
