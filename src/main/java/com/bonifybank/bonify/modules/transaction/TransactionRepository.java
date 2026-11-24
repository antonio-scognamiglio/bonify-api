package com.bonifybank.bonify.modules.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {
    Page<TransactionEntity> findByAccount_Id (UUID accountId, Pageable pageable);

    Page<TransactionEntity> findByAccount_IdAndCreatedGreaterThanEqual (UUID accountId, Instant from, Pageable pageable);

    Page<TransactionEntity> findByAccount_IdAndCreatedAtLessThanEqual(UUID accountId, Instant to, Pageable pageable);

    Page<TransactionEntity> findByAccount_IdAndCreatedAtBetween(UUID accountId, Instant from, Instant to, Pageable pageable);
}
