package com.bonifybank.bonify.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FR-AUTH-02 input.
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // No minimum length here: a short wrong password is a 401 like any other wrong password
        @NotBlank String password) {
}
