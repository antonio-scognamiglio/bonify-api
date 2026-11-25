package com.bonifybank.bonify.modules.auth;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.bonifybank.bonify.modules.customer.CustomerEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One refresh token (FR-AUTH-02 / 04 / 05). Every login starts a new family (the session);
 * every refresh marks the presented token as used and adds a new token to the same family.
 * Used tokens are kept until they expire, so that a reuse can be detected (FR-AUTH-06).
 * Only the SHA-256 of the raw token is stored.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_customer", columnList = "customer_id"),
        @Index(name = "idx_refresh_tokens_family", columnList = "family_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    // SHA-256 (hex) of the raw token
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // Session id: shared by all the tokens created by rotation from the same login
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Set when the token is swapped for a new one: presenting it again is a reuse
    @Column(name = "used_at")
    private Instant usedAt;

    // Set on logout, new login, password change or detected reuse
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
