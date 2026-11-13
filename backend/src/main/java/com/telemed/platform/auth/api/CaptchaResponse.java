package com.telemed.platform.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CaptchaResponse(
        @JsonProperty("captcha_id") String captchaId,
        String image) {
}
