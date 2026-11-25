package com.bonifybank.bonify.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FR-AUTH-04 / FR-AUTH-05 input: the refresh token returned by login or by the last refresh.
 */
public record RefreshTokenRequest(@NotBlank @Size(max = 100) String refreshToken) {
}
