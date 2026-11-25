package com.bonifybank.bonify.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FR-AUTH-04 / FR-AUTH-05 input: the refresh token returned by login or by the last refresh.
 */
@Schema(description = "The refresh token of the session")
public record RefreshTokenRequest(
        @Schema(description = "The `refreshToken` returned by login or by the last refresh",
                example = "q8Xc1Zt4Rk2Lm9Np0Vb7Ws3Ed6Fg5Hj")
        @NotBlank @Size(max = 100) String refreshToken) {
}
