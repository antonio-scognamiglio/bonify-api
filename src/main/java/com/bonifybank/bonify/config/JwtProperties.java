package com.bonifybank.bonify.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings (bonify.jwt.* in application.properties).
 *
 * @param secret     HMAC key for HS256, read from the JWT_SECRET environment variable (NFR-01)
 * @param issuer     value written in the "iss" claim and checked on every request
 * @param expiration        access token validity (NFR-02: 5 minutes)
 * @param refreshExpiration refresh token validity; every refresh rotates the token and restarts this window
 */
@ConfigurationProperties("bonify.jwt")
public record JwtProperties(String secret, String issuer, Duration expiration, Duration refreshExpiration) {

    // HS256 requires a key of at least 256 bits
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("bonify.jwt.secret (JWT_SECRET) must be at least "
                    + MIN_SECRET_BYTES + " bytes long");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("bonify.jwt.issuer must not be blank");
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("bonify.jwt.expiration must be a positive duration");
        }
        if (refreshExpiration == null || refreshExpiration.isNegative() || refreshExpiration.isZero()) {
            throw new IllegalStateException("bonify.jwt.refresh-expiration must be a positive duration");
        }
    }
}
