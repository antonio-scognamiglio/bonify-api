package com.bonifybank.bonify.exception.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        LocalDateTime timestamp) {
}
