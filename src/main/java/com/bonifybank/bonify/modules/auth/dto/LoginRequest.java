package com.bonifybank.bonify.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FR-AUTH-02 input.
 */
@Schema(description = "Login credentials")
public record LoginRequest(
        @Schema(description = "Email used at registration (case-insensitive)", example = "mario.rossi@example.com")
        @NotBlank @Email @Size(max = 254) String email,
        // No minimum length here: a short wrong password is a 401 like any other wrong password
        @Schema(description = "Password", example = "Str0ng-password", format = "password")
        @NotBlank String password) {
}
