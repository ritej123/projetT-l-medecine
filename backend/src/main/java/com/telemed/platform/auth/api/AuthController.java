package com.telemed.platform.auth.api;

import com.telemed.platform.auth.service.AuthService;
import com.telemed.platform.user.api.CreateDoctorRequest;
import com.telemed.platform.user.service.AccountProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountProvisioningService accountProvisioningService;

    public AuthController(AuthService authService, AccountProvisioningService accountProvisioningService) {
        this.authService = authService;
        this.accountProvisioningService = accountProvisioningService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse registerPatient(@Valid @RequestBody RegisterPatientRequest request) {
        accountProvisioningService.registerPatient(request.email(), request.password());
        return authService.login(new LoginRequest(request.email(), request.password()));
    }

    @PostMapping("/register/doctor")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void registerDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        accountProvisioningService.registerDoctorApplication(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }
}
