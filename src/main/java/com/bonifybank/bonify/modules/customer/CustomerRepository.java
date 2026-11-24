package com.bonifybank.bonify.modules.customer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    // Emails are stored in lower case (FR-AUTH-01): callers must pass a lower-case email
    Optional<CustomerEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
