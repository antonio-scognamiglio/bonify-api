package com.bonifybank.bonify.modules.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    Page<TransactionEntity> findByAccount_Id (Long accountId, Pageable pageable);

    Page<TransactionEntity> findByAccount_IdAndCreatedAtGreaterThanEqual (Long accountId, Instant from, Pageable pageable);

    Page<TransactionEntity> findByAccount_IdAndCreatedAtLessThanEqual(Long accountId, Instant to, Pageable pageable);

    Page<TransactionEntity> findByAccount_IdAndCreatedAtBetween(Long accountId, Instant from, Instant to, Pageable pageable);
}
