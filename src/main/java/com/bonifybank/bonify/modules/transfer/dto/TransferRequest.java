package com.bonifybank.bonify.modules.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Transfer to send")
public record TransferRequest(
        @Schema(description = "IBAN of one of your ACTIVE accounts", example = "IT60X0542811101000000123456")
        @NotBlank @Size(min = 15, max = 34)
        String fromIban,
        @Schema(description = "IBAN of the destination ACTIVE account, different from the source",
                example = "IT60X0542811101000000654321")
        @NotBlank @Size(min = 15, max = 34)
        String toIban,
        @Schema(description = "Amount in EUR, at least 0.01, at most 2 decimals", example = "150.00")
        @NotNull
        @DecimalMin(value = "0.01", inclusive = true)
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,
        @Schema(description = "Optional note shown to both sides", example = "Rent")
        @Size(max = 140)
        String description
) {
}
