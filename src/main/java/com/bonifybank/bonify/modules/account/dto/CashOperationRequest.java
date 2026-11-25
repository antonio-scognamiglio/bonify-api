package com.bonifybank.bonify.modules.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Amount of a deposit or a withdrawal")
public record CashOperationRequest (
        @Schema(description = "Amount in EUR, at least 0.01, at most 2 decimals", example = "250.00")
        @NotNull
        @DecimalMin(value = "0.01", inclusive = true)
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,
        @Schema(description = "Optional note shown in the transaction history", example = "Salary")
        @Size(max = 140) String description
){
}
