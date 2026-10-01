package com.clinicaserena.auth.dto;

public record UnifiedLoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String accountType,
        String role
) {
}
