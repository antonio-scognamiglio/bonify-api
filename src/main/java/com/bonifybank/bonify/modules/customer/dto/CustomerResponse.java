package com.bonifybank.bonify.modules.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String taxCode,
        String role,
        boolean active,
        Instant createdAt
) {
}
