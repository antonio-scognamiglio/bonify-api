package com.bonifybank.bonify.modules.transfer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Completed transfer")
public record TransferResponse(
        @Schema(description = "Transfer id", example = "9b2e4f6a-1c3d-4e5f-8a7b-0c1d2e3f4a5b")
        UUID id,
        @Schema(example = "IT60X0542811101000000123456")
        String fromIban,
        @Schema(example = "IT60X0542811101000000654321")
        String toIban,
        @Schema(example = "150.00")
        BigDecimal amount,
        @Schema(example = "Rent")
        String description,
        @Schema(description = "Transfers are executed synchronously: always COMPLETED",
                allowableValues = {"COMPLETED"}, example = "COMPLETED")
        String status,
        @Schema(example = "2026-11-25T11:00:00Z")
        Instant createdAt
) {
}
