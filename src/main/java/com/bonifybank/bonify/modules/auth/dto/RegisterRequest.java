package com.bonifybank.bonify.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FR-AUTH-01 input. The service normalizes email (lower case), tax code (upper case) and strips
 * names and phone, so only format/length validation happens here.
 */
@Schema(description = "Data of the new customer")
public record RegisterRequest(
        @Schema(description = "First name", example = "Mario")
        @NotBlank @Size(min = 1, max = 50) String firstName,
        @Schema(description = "Last name", example = "Rossi")
        @NotBlank @Size(min = 1, max = 50) String lastName,
        @Schema(description = "Login email, unique (stored in lower case)", example = "mario.rossi@example.com")
        @NotBlank @Email @Size(max = 254) String email,
        // Same format as the profile update (FR-CUS-02); max 20 to fit the column
        @Schema(description = "7-20 characters: digits, spaces and dashes, optional leading +",
                example = "+39 333 1234567")
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$", message = "Invalid phone number format")
        String phoneNumber,
        // Italian codice fiscale: 6 letters (surname, name), birth year, month letter, day + sex,
        // town code, check letter. Digits may be replaced by letters (omocodia). Case-insensitive.
        @Schema(description = "Italian tax code (codice fiscale), 16 characters, unique; case-insensitive",
                example = "RSSMRA85T10A562S")
        @NotBlank
        @Pattern(regexp = "^(?i)[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$",
                message = "Invalid tax code format")
        String taxCode,
        @Schema(description = "At least 8 characters", example = "Str0ng-password", format = "password")
        @NotBlank @Size(min = 8) String password) {
}
