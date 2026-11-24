package com.bonifybank.bonify.modules.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UUID customerId,
        String email,
        String role
) {
}
