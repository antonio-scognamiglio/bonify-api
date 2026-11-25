package com.bonifybank.bonify.modules.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current account")
public record AccountResponse(
        @Schema(description = "Internal id", example = "1")
        Long id,
        @Schema(description = "IBAN generated at opening; use it in the account URLs and in transfers",
                example = "IT60X0542811101000000123456")
        String iban,
        @Schema(description = "Always EUR", example = "EUR")
        String currency,
        @Schema(description = "Current balance, never negative", example = "850.00")
        BigDecimal balance,
        @Schema(description = "BLOCKED accounts refuse deposits, withdrawals and transfers",
                allowableValues = {"ACTIVE", "BLOCKED"}, example = "ACTIVE")
        String status,
        @Schema(description = "Owner id", example = "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01")
        UUID customerId,
        @Schema(example = "2026-11-25T10:00:00Z")
        Instant createdAt
) {
}
