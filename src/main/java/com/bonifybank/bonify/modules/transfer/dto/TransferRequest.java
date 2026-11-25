package com.bonifybank.bonify.modules.transfer.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank @Size(min = 15, max = 34)
        String fromIban,
        @NotBlank @Size(min = 15, max = 34)
        String toIban,
        @NotNull
        @DecimalMin(value = "0.01", inclusive = true)
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,
        @Size(max = 140)
        String description
) {
}
