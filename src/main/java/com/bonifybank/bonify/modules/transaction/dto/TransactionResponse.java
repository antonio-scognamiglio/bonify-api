package com.bonifybank.bonify.modules.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        // Set only for TRANSFER_IN / TRANSFER_OUT lines (FR-TRX-01)
        UUID transferId,
        Instant createdAt
) {
}
