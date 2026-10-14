package com.telemed.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.auth.api.LoginRequest;
import com.telemed.platform.auth.domain.RefreshToken;
import com.telemed.platform.auth.repository.RefreshTokenRepository;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private JwtDecoder jwtDecoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtEncoder,
                jwtDecoder,
                Clock.systemUTC(),
                Duration.ofMinutes(15),
                Duration.ofDays(14));
    }

    @Test
    void issuesSeparateAccessAndRefreshTokensForValidCredentials() {
        User user = new User("patient@example.test", "stored-hash", Role.PATIENT);
        ReflectionTestUtils.setField(user, "id", java.util.UUID.randomUUID());
        when(userRepository.findByEmail("patient@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("long-enough-password", "stored-hash")).thenReturn(true);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(token("access-token"), token("refresh-token"));

        var response = authService.login(new LoginRequest("patient@example.test", "long-enough-password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void rejectsInvalidCredentialsWithoutIssuingTokens() {
        when(userRepository.findByEmail("unknown@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("unknown@example.test", "long-enough-password")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtEncoder, never()).encode(any());
    }

    @Test
    void rotatesRefreshTokenAndRevokesThePresentedToken() {
        UUID userId = UUID.randomUUID();
        User user = new User("patient@example.test", "stored-hash", Role.PATIENT);
        ReflectionTestUtils.setField(user, "id", userId);
        RefreshToken storedToken = new RefreshToken(user, "stored-token-hash", Instant.now().plusSeconds(3600));
        when(jwtDecoder.decode("presented-refresh-token")).thenReturn(Jwt.withTokenValue("presented-refresh-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("token_type", "refresh")
                .build());
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(storedToken));
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(token("new-access-token"), token("new-refresh-token"));

        var response = authService.refresh("presented-refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    private Jwt token(String value) {
        return Jwt.withTokenValue(value)
                .header("alg", "HS256")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .build();
    }
}
