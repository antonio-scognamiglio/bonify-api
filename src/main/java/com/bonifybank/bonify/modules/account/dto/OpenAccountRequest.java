package com.bonifybank.bonify.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
        @NotBlank @Size(min = 3, max =3) String currency
) {
}
