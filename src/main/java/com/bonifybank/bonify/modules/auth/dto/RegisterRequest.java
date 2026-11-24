package com.bonifybank.bonify.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min=8 , max = 100) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$", message = "Invalid phone number format")
        String phoneNumber,
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9]{6,20}$", message = "Invalid tax code format")
        @Size(min = 6, max = 20)
        String taxCode
) {
}
