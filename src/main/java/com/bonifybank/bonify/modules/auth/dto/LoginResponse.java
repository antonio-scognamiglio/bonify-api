package com.bonifybank.bonify.modules.auth.dto;

/**
 * Output of login (FR-AUTH-02) and refresh (FR-AUTH-04): the access token with its type and
 * validity in seconds, plus the refresh token that replaces any previous one.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn) {
}
