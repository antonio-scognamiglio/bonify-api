package com.bonifybank.bonify.modules.transfer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String fromIban,
        String toIban,
        BigDecimal amount,
        String description,
        String status,
        Instant createdAt
) {
}
