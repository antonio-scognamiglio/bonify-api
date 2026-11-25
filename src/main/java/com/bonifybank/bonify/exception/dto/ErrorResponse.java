package com.bonifybank.bonify.exception.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RFC 7807 problem detail with the extra "code" field (functional-analysis.md section 8).
 *
 * @param errors one entry per invalid field or parameter; only present on VALIDATION_ERROR
 *               responses caused by input validation
 */
@Schema(description = "RFC 7807 problem detail, sent as application/problem+json")
public record ErrorResponse(
        @Schema(description = "URI identifying the error type", example = "https://bonify.example/errors/insufficient-funds")
        String type,
        @Schema(description = "Short, fixed description of the error type", example = "Insufficient funds")
        String title,
        @Schema(description = "HTTP status", example = "422")
        int status,
        @Schema(description = "Human-readable explanation of this occurrence",
                example = "Account IT60X0542811101000000123456 has 850.00 EUR, 1000.00 EUR requested")
        String detail,
        @Schema(description = "Path of the request", example = "/api/v1/accounts/IT60X0542811101000000123456/withdraw")
        String instance,
        @Schema(description = "Stable error code to switch on in the client", example = "INSUFFICIENT_FUNDS",
                allowableValues = {"VALIDATION_ERROR", "SAME_ACCOUNT", "WRONG_CURRENT_PASSWORD", "UNAUTHORIZED",
                        "FORBIDDEN", "NOT_FOUND", "METHOD_NOT_ALLOWED", "EMAIL_ALREADY_USED", "TAX_CODE_ALREADY_USED",
                        "IDEMPOTENCY_KEY_REUSED", "UNSUPPORTED_MEDIA_TYPE", "INSUFFICIENT_FUNDS", "ACCOUNT_BLOCKED",
                        "INTERNAL_ERROR"})
        String code,
        @Schema(description = "When the error happened (UTC)", example = "2026-11-25T10:45:00Z")
        Instant timestamp,
        @Schema(description = "Only on VALIDATION_ERROR from input validation: one entry per invalid field")
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldError> errors) {

    @Schema(description = "One invalid field or parameter")
    public record FieldError(
            @Schema(example = "amount") String field,
            @Schema(example = "must be greater than or equal to 0.01") String message) {
    }
}
