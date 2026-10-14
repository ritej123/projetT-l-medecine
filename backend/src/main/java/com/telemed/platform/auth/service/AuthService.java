package com.telemed.platform.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.telemed.platform.auth.api.LoginRequest;
import com.telemed.platform.auth.api.TokenResponse;
import com.telemed.platform.auth.domain.RefreshToken;
import com.telemed.platform.auth.repository.RefreshTokenRepository;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;
    private final long accessTokenSeconds;
    private final long refreshTokenSeconds;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder jwtDecoder,
            Clock clock,
            @Value("${app.security.jwt.access-token-ttl:PT15M}") java.time.Duration accessTokenTtl,
            @Value("${app.security.jwt.refresh-token-ttl:P14D}") java.time.Duration refreshTokenTtl) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
        this.accessTokenSeconds = accessTokenTtl.toSeconds();
        this.refreshTokenSeconds = refreshTokenTtl.toSeconds();
        if (accessTokenSeconds <= 0 || refreshTokenSeconds <= accessTokenSeconds) {
            throw new IllegalArgumentException("JWT token lifetimes must be positive and refresh must outlive access.");
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.email()) || !StringUtils.hasText(request.password())) {
            throw new BadCredentialsException("Adresse e-mail ou mot de passe invalide.");
        }
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .filter(account -> account.isEnabled() && passwordEncoder.matches(
                        request.password(), account.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Adresse e-mail ou mot de passe invalide."));
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String presentedToken) {
        if (!StringUtils.hasText(presentedToken)) {
            throw new BadCredentialsException("Jeton de renouvellement invalide.");
        }
        Jwt jwt = decodeRefreshToken(presentedToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashForUpdate(hash(presentedToken))
                .filter(token -> !token.isRevoked() && token.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new BadCredentialsException("Jeton de renouvellement invalide."));
        User user = storedToken.getUser();
        if (!user.isEnabled() || !user.getId().toString().equals(jwt.getSubject())) {
            throw new BadCredentialsException("Jeton de renouvellement invalide.");
        }
        storedToken.revoke();
        return issueTokens(user);
    }

    @Transactional
    public void logout(String presentedToken) {
        if (!StringUtils.hasText(presentedToken)) {
            throw new BadCredentialsException("Jeton de renouvellement invalide.");
        }
        decodeRefreshToken(presentedToken);
        refreshTokenRepository.findByTokenHashForUpdate(hash(presentedToken))
                .filter(token -> !token.isRevoked())
                .orElseThrow(() -> new BadCredentialsException("Jeton de renouvellement invalide."))
                .revoke();
    }

    private Jwt decodeRefreshToken(String presentedToken) {
        if (!StringUtils.hasText(presentedToken)) {
            throw new BadCredentialsException("Jeton de renouvellement invalide.");
        }
        try {
            Jwt jwt = jwtDecoder.decode(presentedToken);
            if (!REFRESH_TOKEN.equals(jwt.getClaimAsString("token_type"))) {
                throw new BadCredentialsException("Jeton de renouvellement invalide.");
            }
            return jwt;
        } catch (org.springframework.security.oauth2.jwt.JwtException exception) {
            throw new BadCredentialsException("Jeton de renouvellement invalide.");
        }
    }

    private TokenResponse issueTokens(User user) {
        Instant now = clock.instant();
        String accessToken = encode(user, now, accessTokenSeconds, ACCESS_TOKEN);
        String refreshToken = encode(user, now, refreshTokenSeconds, REFRESH_TOKEN);
        refreshTokenRepository.save(new RefreshToken(
                user, hash(refreshToken), now.plusSeconds(refreshTokenSeconds)));
        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenSeconds);
    }

    private String encode(User user, Instant now, long lifetimeSeconds, String tokenType) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("telemed-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(lifetimeSeconds))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .claim("token_type", tokenType)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
