package com.bonifybank.bonify.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FR-AUTH-01 input. The service normalizes email (lower case), tax code (upper case) and strips
 * names and phone, so only format/length validation happens here.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 1, max = 50) String firstName,
        @NotBlank @Size(min = 1, max = 50) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        // Same format as the profile update (FR-CUS-02); max 20 to fit the column
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$", message = "Invalid phone number format")
        String phoneNumber,
        // Italian codice fiscale: 6 letters (surname, name), birth year, month letter, day + sex,
        // town code, check letter. Digits may be replaced by letters (omocodia). Case-insensitive.
        @NotBlank
        @Pattern(regexp = "^(?i)[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$",
                message = "Invalid tax code format")
        String taxCode,
        @NotBlank @Size(min = 8) String password) {
}
