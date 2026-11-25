package com.bonifybank.bonify.modules.auth;

/** Token pair issued by login and refresh. */
public record AuthTokens(String accessToken, String refreshToken) {
}
