package com.bonifybank.bonify.modules.auth.dto;

/**
 * FR-AUTH-02 output: the issued token plus its type and validity in seconds.
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
