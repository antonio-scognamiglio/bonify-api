package com.bonifybank.bonify.modules.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Current and new password")
public record ChangePasswordRequest(
        @Schema(description = "The password in use now", example = "Str0ng-password", format = "password")
        @NotBlank String currentPassword,
        @Schema(description = "8-100 characters, different from the current one", example = "Even-str0nger-password",
                format = "password")
        @NotBlank @Size (min = 8, max = 100) String newPassword
) {
}
