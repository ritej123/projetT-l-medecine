package com.telemed.platform.auth.api;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemed.platform.auth.service.AuthService;
import com.telemed.platform.auth.service.CaptchaService;
import com.telemed.platform.user.service.AccountProvisioningService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AuthControllerTest {

    @Test
    void verifiesCaptchaBeforeCallingPasswordAuthentication() {
        AuthService authService = mock(AuthService.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        AuthController controller = new AuthController(
                authService, captchaService, mock(AccountProvisioningService.class));
        LoginRequest request = new LoginRequest(
                "patient@example.test", "a-long-enough-password", "captcha-id", "abc123");
        when(authService.login(request)).thenReturn(new TokenResponse("access", "refresh", "Bearer", 900));

        controller.login(request);

        InOrder calls = inOrder(captchaService, authService);
        calls.verify(captchaService).verify("captcha-id", "abc123");
        calls.verify(authService).login(request);
    }

    @Test
    void doesNotCheckPasswordWhenCaptchaIsInvalid() {
        AuthService authService = mock(AuthService.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        AuthController controller = new AuthController(
                authService, captchaService, mock(AccountProvisioningService.class));
        LoginRequest request = new LoginRequest(
                "patient@example.test", "a-long-enough-password", "captcha-id", "wrong");
        org.mockito.Mockito.doThrow(new com.telemed.platform.common.exception.InvalidRequestException("invalid"))
                .when(captchaService).verify("captcha-id", "wrong");

        assertThatThrownBy(() -> controller.login(request))
                .isInstanceOf(com.telemed.platform.common.exception.InvalidRequestException.class);
        verifyNoInteractions(authService);
    }
}
