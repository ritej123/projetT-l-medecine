package com.telemed.platform.auth.api;

import com.telemed.platform.auth.service.CaptchaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping("/api/captcha")
    public ResponseEntity<CaptchaResponse> createCaptcha() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(captchaService.create());
    }
}
