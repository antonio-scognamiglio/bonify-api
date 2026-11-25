package com.bonifybank.bonify.modules.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FR-CUS-02 input: partial update, null fields stay unchanged. Same rules as registration.
 * Unknown fields such as "role" or "password" are ignored by Jackson, so they can never be changed here.
 */
@Schema(description = "Fields to change; omitted or null fields stay unchanged")
public record UpdateProfileRequest(
        // @NotBlank would also reject null (field not sent): "at least one non-blank character" is a pattern
        @Schema(description = "New first name", example = "Mario")
        @Size(min = 1, max = 50) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String firstName,
        @Schema(description = "New last name", example = "Bianchi")
        @Size(min = 1, max = 50) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String lastName,
        // Must not belong to another customer (BR-10), checked by the service
        @Schema(description = "New email, must not belong to another customer", example = "mario.bianchi@example.com")
        @Email @Size(min = 1, max = 254) String email,
        // Max 20 to fit the column: the pattern alone allows 21 characters with the leading "+"
        @Schema(description = "7-20 characters: digits, spaces and dashes, optional leading +",
                example = "+39 347 7654321")
        @Size(max = 20)
        @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$", message = "Invalid phone number format")
        String phoneNumber
) {
}
