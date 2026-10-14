package com.telemed.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.core.Authentication;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void decoderRejectsRefreshTokensAsApiCredentials() {
        var key = securityConfig.jwtSecretKey("a-long-test-secret-that-is-at-least-32-bytes");
        JwtEncoder encoder = securityConfig.jwtEncoder(key);
        UserRepository userRepository = mock(UserRepository.class);
        UUID accountId = UUID.randomUUID();
        when(userRepository.findById(accountId))
                .thenReturn(Optional.of(new User("admin@example.test", "encoded-password", Role.ADMIN)));
        JwtDecoder decoder = securityConfig.jwtDecoder(key, userRepository);
        Instant now = Instant.now();
        Jwt refreshToken = encode(encoder, now, "refresh", accountId);

        assertThatThrownBy(() -> decoder.decode(refreshToken.getTokenValue()))
                .isInstanceOf(JwtException.class);
        Jwt accessToken = encode(encoder, now, "access", accountId);
        assertThatCode(() -> decoder.decode(accessToken.getTokenValue())).doesNotThrowAnyException();
    }

    @Test
    void corsUsesOnlyConfiguredOrigins() {
        var source = securityConfig.corsConfigurationSource("https://app.example.test");
        var cors = source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1"));

        assertThat(cors.getAllowedOrigins()).containsExactly("https://app.example.test");
        assertThat(cors.getAllowCredentials()).isFalse();
        assertThatThrownBy(() -> securityConfig.corsConfigurationSource("*"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapsTokenRolesToSpringRoleAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("roles", List.of("ADMIN"))
                .build();
        Authentication authentication = securityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    private Jwt encode(JwtEncoder encoder, Instant now, String tokenType, UUID accountId) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .subject(accountId.toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .claim("token_type", tokenType)
                        .build()));
    }
}
