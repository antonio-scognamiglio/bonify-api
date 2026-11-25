package com.bonifybank.bonify.modules.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    // The customer is needed right away to issue the access token
    @EntityGraph(attributePaths = "customer")
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Marks the token as used only if it is still active (compare-and-set), so two concurrent
     * refreshes with the same token cannot both succeed.
     *
     * @return 1 if marked, 0 if another request used or revoked it in the meantime
     */
    @Modifying
    @Query("""
            update RefreshTokenEntity t
               set t.usedAt = :now
             where t.id = :id and t.usedAt is null and t.revokedAt is null
            """)
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** Ends one session: every token of the family. */
    @Modifying
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :now
             where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /** Ends every session of the customer (new login, password change). */
    @Modifying
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :now
             where t.customer.id = :customerId and t.revokedAt is null
            """)
    int revokeAllByCustomer(@Param("customerId") UUID customerId, @Param("now") Instant now);

    /** Housekeeping: an expired token is rejected anyway, so its row is no longer needed. */
    @Modifying
    @Query("delete from RefreshTokenEntity t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
