package com.bonifybank.bonify.modules.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "New status of the account")
public record UpdateAccountStatusRequest (
        @Schema(description = "BLOCKED to freeze the account, ACTIVE to unblock it",
                allowableValues = {"ACTIVE", "BLOCKED"}, example = "BLOCKED")
        @NotBlank String status
){
}
