package com.bonifybank.bonify.exception.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 7807 problem detail with the extra "code" field (functional-analysis.md section 8).
 *
 * @param errors one entry per invalid field or parameter; only present on VALIDATION_ERROR
 *               responses caused by input validation
 */
public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }
}
