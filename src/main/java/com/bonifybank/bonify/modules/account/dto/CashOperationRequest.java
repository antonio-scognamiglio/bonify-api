package com.bonifybank.bonify.modules.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CashOperationRequest (
        @NotNull
        @DecimalMin(value = "0.01", inclusive = true)
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,
        @Size(max = 140) String description
){
}
