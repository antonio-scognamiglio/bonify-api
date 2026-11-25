package com.bonifybank.bonify.modules.customer.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Customer data (the password is never returned)")
public record CustomerResponse(
        @Schema(description = "Customer id", example = "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01")
        UUID id,
        @Schema(example = "mario.rossi@example.com")
        String email,
        @Schema(example = "Mario")
        String firstName,
        @Schema(example = "Rossi")
        String lastName,
        @Schema(example = "+39 333 1234567")
        String phoneNumber,
        @Schema(example = "RSSMRA85T10A562S")
        String taxCode,
        @Schema(description = "Role of the customer", allowableValues = {"CUSTOMER", "ADMIN"}, example = "CUSTOMER")
        String role,
        @Schema(example = "2026-11-25T09:00:00Z")
        Instant createdAt,
        @Schema(example = "2026-11-25T09:30:00Z")
        Instant updatedAt
) {
}
