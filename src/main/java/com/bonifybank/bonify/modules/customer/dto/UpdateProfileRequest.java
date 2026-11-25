package com.bonifybank.bonify.modules.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FR-CUS-02 input: partial update, null fields stay unchanged. Same rules as registration.
 * Unknown fields such as "role" or "password" are ignored by Jackson, so they can never be changed here.
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 50) String firstName,
        @Size(min = 1, max = 50) String lastName,
        // Must not belong to another customer (BR-10), checked by the service
        @Email @Size(max = 254) String email,
        @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$", message = "Invalid phone number format")
        String phoneNumber
) {
}
