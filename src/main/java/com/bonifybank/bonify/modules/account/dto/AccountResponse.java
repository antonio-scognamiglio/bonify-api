package com.bonifybank.bonify.modules.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String iban,
        String currency,
        BigDecimal balance,
        String status,
        UUID customerId,
        Instant createdAt
) {
}
