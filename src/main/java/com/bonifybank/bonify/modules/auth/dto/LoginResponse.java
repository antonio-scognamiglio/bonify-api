package com.bonifybank.bonify.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Output of login (FR-AUTH-02) and refresh (FR-AUTH-04): the access token with its type and
 * validity in seconds, plus the refresh token that replaces any previous one.
 */
@Schema(description = "Token pair of the session")
public record LoginResponse(
        @Schema(description = "JWT to send as `Authorization: Bearer <accessToken>` (paste it in Authorize)")
        String accessToken,
        @Schema(description = "Always `Bearer`", example = "Bearer")
        String tokenType,
        @Schema(description = "Validity of the access token, in seconds", example = "300")
        long expiresIn,
        @Schema(description = "Opaque token for `/auth/refresh` and `/auth/logout`; single use (rotated)",
                example = "q8Xc1Zt4Rk2Lm9Np0Vb7Ws3Ed6Fg5Hj")
        String refreshToken,
        @Schema(description = "Validity of the refresh token, in seconds", example = "604800")
        long refreshExpiresIn) {
}
