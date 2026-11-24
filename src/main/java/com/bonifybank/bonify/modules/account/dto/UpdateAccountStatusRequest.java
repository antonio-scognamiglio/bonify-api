package com.bonifybank.bonify.modules.account.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccountStatusRequest (
        @NotBlank String status
){
}
