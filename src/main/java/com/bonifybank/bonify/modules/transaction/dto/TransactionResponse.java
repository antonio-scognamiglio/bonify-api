package com.bonifybank.bonify.modules.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One movement of an account")
public record TransactionResponse(
        @Schema(example = "3")
        Long id,
        @Schema(description = "Kind of movement", allowableValues = {"DEPOSIT", "WITHDRAW", "TRANSFER_IN", "TRANSFER_OUT"},
                example = "TRANSFER_OUT")
        String type,
        @Schema(description = "Always positive: the type tells the direction", example = "150.00")
        BigDecimal amount,
        @Schema(description = "Balance of the account right after this movement", example = "850.00")
        BigDecimal balanceAfter,
        @Schema(example = "Rent")
        String description,
        // Set only for TRANSFER_IN / TRANSFER_OUT lines (FR-TRX-01)
        @Schema(description = "Id of the transfer, only for TRANSFER_IN / TRANSFER_OUT; null otherwise",
                example = "9b2e4f6a-1c3d-4e5f-8a7b-0c1d2e3f4a5b", nullable = true)
        UUID transferId,
        @Schema(example = "2026-11-25T11:00:00Z")
        Instant createdAt
) {
}
