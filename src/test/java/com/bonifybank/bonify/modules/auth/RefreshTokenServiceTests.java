package com.bonifybank.bonify.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.bonifybank.bonify.config.JwtProperties;
import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;

class RefreshTokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration REFRESH_VALIDITY = Duration.ofDays(7);

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final JwtProperties properties = new JwtProperties(
            "test-secret-that-is-at-least-32-bytes-long", "bonify", Duration.ofMinutes(60), REFRESH_VALIDITY);
    private final CustomerEntity customer = CustomerEntity.builder()
            .id(UUID.randomUUID())
            .email("anna@example.com")
            .role(CustomerRole.CUSTOMER)
            .build();

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueRevokesPreviousSessionsAndStoresOnlyTheHash() {
        String token = service.issue(customer);

        InOrder order = inOrder(repository);
        order.verify(repository).revokeAllByCustomer(customer.getId(), NOW);
        RefreshTokenEntity saved = savedToken(order);

        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(token)).isNotEqualTo(token);
        assertThat(saved.getCustomer()).isSameAs(customer);
        assertThat(saved.getFamilyId()).isNotNull();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(REFRESH_VALIDITY));
    }

    @Test
    void eachLoginStartsANewFamily() {
        service.issue(customer);
        service.issue(customer);

        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getFamilyId())
                .isNotEqualTo(saved.getAllValues().get(1).getFamilyId());
        assertThat(saved.getAllValues().get(0).getTokenHash())
                .isNotEqualTo(saved.getAllValues().get(1).getTokenHash());
    }

    @Test
    void rotateMarksTheTokenUsedAndIssuesANewOneInTheSameFamily() {
        RefreshTokenEntity current = stored("old-token", NOW.plus(Duration.ofDays(1)));
        when(repository.markUsed(current.getId(), NOW)).thenReturn(1);

        RefreshTokenService.Rotation rotation = service.rotate("old-token");

        RefreshTokenEntity created = savedToken(inOrder(repository));
        assertThat(rotation.customer()).isSameAs(customer);
        assertThat(rotation.refreshToken()).isNotEqualTo("old-token");
        assertThat(created.getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.refreshToken()));
        assertThat(created.getFamilyId()).isEqualTo(current.getFamilyId());
        assertThat(created.getExpiresAt()).isEqualTo(NOW.plus(REFRESH_VALIDITY));
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertUnauthorized(() -> service.rotate("unknown"));
    }

    @Test
    void rotateRejectsRevokedToken() {
        RefreshTokenEntity current = stored("old-token", NOW.plus(Duration.ofDays(1)));
        current.setRevokedAt(NOW.minusSeconds(60));

        assertUnauthorized(() -> service.rotate("old-token"));
        verify(repository, never()).markUsed(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotateRejectsExpiredToken() {
        stored("old-token", NOW.minusSeconds(1));

        assertUnauthorized(() -> service.rotate("old-token"));
        verify(repository, never()).markUsed(any(), any());
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeSession() {
        RefreshTokenEntity current = stored("old-token", NOW.plus(Duration.ofDays(1)));
        current.setUsedAt(NOW.minusSeconds(60));

        assertUnauthorized(() -> service.rotate("old-token"));
        verify(repository).revokeFamily(current.getFamilyId(), NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void reuseIsDetectedEvenAfterExpiry() {
        RefreshTokenEntity current = stored("old-token", NOW.minusSeconds(1));
        current.setUsedAt(NOW.minus(Duration.ofDays(8)));

        assertUnauthorized(() -> service.rotate("old-token"));
        verify(repository).revokeFamily(current.getFamilyId(), NOW);
    }

    @Test
    void concurrentRefreshWithTheSameTokenIsRejectedWithoutRevoking() {
        RefreshTokenEntity current = stored("old-token", NOW.plus(Duration.ofDays(1)));
        when(repository.markUsed(current.getId(), NOW)).thenReturn(0);

        assertUnauthorized(() -> service.rotate("old-token"));
        verify(repository, never()).save(any());
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void revokeEndsTheFamilyOfTheToken() {
        RefreshTokenEntity current = stored("some-token", NOW.plus(Duration.ofDays(1)));

        service.revoke("some-token");

        verify(repository).revokeFamily(current.getFamilyId(), NOW);
    }

    @Test
    void revokeIgnoresUnknownToken() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.revoke("unknown");

        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void revokeAllEndsEverySessionOfTheCustomer() {
        service.revokeAll(customer.getId());

        verify(repository).revokeAllByCustomer(customer.getId(), NOW);
    }

    @Test
    void cleanupDeletesExpiredTokens() {
        service.deleteExpiredTokens();

        verify(repository).deleteExpiredBefore(NOW);
    }

    private RefreshTokenEntity stored(String token, Instant expiresAt) {
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .tokenHash(RefreshTokenService.hash(token))
                .familyId(UUID.randomUUID())
                .expiresAt(expiresAt)
                .build();
        when(repository.findByTokenHash(RefreshTokenService.hash(token))).thenReturn(Optional.of(entity));
        return entity;
    }

    private RefreshTokenEntity savedToken(InOrder order) {
        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        order.verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static void assertUnauthorized(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCodes.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo(RefreshTokenService.INVALID_REFRESH_TOKEN);
                });
    }
}
