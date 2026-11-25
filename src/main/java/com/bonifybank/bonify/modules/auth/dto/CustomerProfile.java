package com.bonifybank.bonify.modules.auth.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Customer profile as returned by register (FR-AUTH-01).
 * The password hash is never exposed.
 */
@Schema(description = "Profile of the registered customer")
public record CustomerProfile(
        @Schema(description = "Customer id", example = "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01")
        UUID id,
        @Schema(example = "Mario")
        String firstName,
        @Schema(example = "Rossi")
        String lastName,
        @Schema(description = "Lower case", example = "mario.rossi@example.com")
        String email,
        @Schema(example = "+39 333 1234567")
        String phoneNumber,
        @Schema(description = "Upper case", example = "RSSMRA85T10A562S")
        String taxCode) {
}
