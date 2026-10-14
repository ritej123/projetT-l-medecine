package com.telemed.platform.auth.api;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
