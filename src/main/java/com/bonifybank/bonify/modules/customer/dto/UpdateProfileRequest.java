package com.bonifybank.bonify.modules.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FR-CUS-02 input: partial update, null fields stay unchanged. Same rules as registration.
 * Unknown fields such as "role" or "password" are ignored by Jackson, so they can never be changed here.
 */
public record UpdateProfileRequest(
        // @NotBlank would also reject null (field not sent): "at least one non-blank character" is a pattern
        @Size(min = 1, max = 50) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String firstName,
        @Size(min = 1, max = 50) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String lastName,
        // Must not belong to another customer (BR-10), checked by the service
        @Email @Size(min = 1, max = 254) String email,
        // Max 20 to fit the column: the pattern alone allows 21 characters with the leading "+"
        @Size(max = 20)
        @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$", message = "Invalid phone number format")
        String phoneNumber
) {
}
