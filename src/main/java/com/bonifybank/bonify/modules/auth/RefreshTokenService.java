package com.bonifybank.bonify.modules.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bonifybank.bonify.config.JwtProperties;
import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.customer.CustomerEntity;

/**
 * Opaque refresh tokens with rotation and reuse detection, one active session per customer.
 * <ul>
 *   <li>The token is 256 random bits (base64url); only its SHA-256 is stored (refresh_tokens).</li>
 *   <li>Login revokes every previous session and starts a new family.</li>
 *   <li>Refresh marks the presented token as used and issues a new one in the same family.</li>
 *   <li>Presenting a token that was already used means it was copied (stolen or leaked):
 *       the whole family is revoked, so both the thief and the legitimate client must log in again.</li>
 * </ul>
 * Callers must run inside a transaction (the repository updates are @Modifying queries).
 */
@Service
public class RefreshTokenService {

    // Same message for every rejection: the client just has to log in again
    static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties properties) {
        this(refreshTokenRepository, properties, Clock.systemUTC());
    }

    // Visible for tests: lets them move time forward to check expiry
    RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new session for the customer, revoking any previous one. Returns the raw token. */
    public String issue(CustomerEntity customer) {
        refreshTokenRepository.revokeAllByCustomer(customer.getId(), clock.instant());
        return create(customer, UUID.randomUUID());
    }

    /**
     * Swaps a valid refresh token for a new one in the same session.
     * <p>
     * When a reuse is detected the family is revoked <b>and</b> an exception is thrown: the caller's
     * transaction must not roll back on {@link BusinessException}, or the revocation would be lost.
     *
     * @return the customer owning the session and the new raw token
     * @throws BusinessException UNAUTHORIZED if the token is unknown, revoked, expired or already used
     */
    public Rotation rotate(String token) {
        Instant now = clock.instant();
        RefreshTokenEntity current = refreshTokenRepository.findByTokenHash(hash(token))
                .orElseThrow(RefreshTokenService::invalidToken);

        if (current.getRevokedAt() != null) {
            throw invalidToken();
        }
        if (current.getUsedAt() != null) {
            // Checked before expiry: an old copy of the token is a reuse even if it has expired
            log.warn("Refresh token reuse detected: revoking session {} of customer {}",
                    current.getFamilyId(), current.getCustomer().getId());
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw invalidToken();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }
        if (refreshTokenRepository.markUsed(current.getId(), now) != 1) {
            // A concurrent request with the same token won the race: reject without revoking,
            // since it is most likely the same client (e.g. two tabs refreshing together)
            throw invalidToken();
        }

        String newToken = create(current.getCustomer(), current.getFamilyId());
        return new Rotation(current.getCustomer(), newToken);
    }

    /** Logout: ends the session owning this token. Unknown tokens are ignored (idempotent). */
    public void revoke(String token) {
        refreshTokenRepository.findByTokenHash(hash(token))
                .ifPresent(found -> refreshTokenRepository.revokeFamily(found.getFamilyId(), clock.instant()));
    }

    /** Ends every session of the customer, e.g. after a password change. */
    public void revokeAll(UUID customerId) {
        refreshTokenRepository.revokeAllByCustomer(customerId, clock.instant());
    }

    /** Refresh token validity in seconds: the "refreshExpiresIn" field of the token response. */
    public long getExpirationSeconds() {
        return properties.refreshExpiration().toSeconds();
    }

    /** Every night at 03:00: deletes the rows of expired tokens, which can no longer be accepted. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(clock.instant());
        log.info("Deleted {} expired refresh tokens", deleted);
    }

    public record Rotation(CustomerEntity customer, String refreshToken) {
    }

    private String create(CustomerEntity customer, UUID familyId) {
        String token = newToken();
        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .customer(customer)
                .tokenHash(hash(token))
                .familyId(familyId)
                .expiresAt(clock.instant().plus(properties.refreshExpiration()))
                .build());
        return token;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // A fast hash is enough here: the token is random and high-entropy, unlike a password
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static BusinessException invalidToken() {
        return new BusinessException(ErrorCodes.UNAUTHORIZED, INVALID_REFRESH_TOKEN);
    }
}
