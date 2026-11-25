package com.bonifybank.bonify.modules.customer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    // Emails are stored in lower case (FR-AUTH-01): callers must pass a lower-case email
    Optional<CustomerEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    // Tax codes are stored in upper case (FR-AUTH-01): callers must pass an upper-case tax code
    boolean existsByTaxCode(String taxCode);

    // Row lock that serialises concurrent logins of the same customer (FR-AUTH-02: one active session)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerEntity c where c.id = :id")
    Optional<CustomerEntity> findByIdForUpdate(@Param("id") UUID id);
}
