package com.bonifybank.bonify.modules.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bonifybank.bonify.config.ApiExamples;
import com.bonifybank.bonify.config.OpenApiConfig;
import com.bonifybank.bonify.exception.dto.ErrorResponse;
import com.bonifybank.bonify.modules.auth.dto.CustomerProfile;
import com.bonifybank.bonify.modules.auth.dto.LoginRequest;
import com.bonifybank.bonify.modules.auth.dto.LoginResponse;
import com.bonifybank.bonify.modules.auth.dto.RefreshTokenRequest;
import com.bonifybank.bonify.modules.auth.dto.RegisterRequest;
import com.bonifybank.bonify.modules.customer.CustomerEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@Validated
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_AUTH)
// Public endpoints: the empty requirement overrides the global bearer one (no lock in Swagger UI)
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // FR-AUTH-01: public registration, returns 201 with the profile (no password)
    @Operation(summary = "Register a new customer", description = """
            Creates a customer with role `CUSTOMER` and returns the profile (never the password).
            Email is stored in lower case and the tax code in upper case: both must be unique.
            The tax code must have the format of an Italian *codice fiscale*.
            """)
    @ApiResponse(responseCode = "201", description = "Customer created",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = CustomerProfile.class),
                    examples = @ExampleObject(value = ApiExamples.CUSTOMER_PROFILE)))
    @ApiResponse(responseCode = "400", description = "Invalid fields (`errors` lists each one)",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.VALIDATION_ERROR_REGISTER)))
    @ApiResponse(responseCode = "409", description = "Email or tax code already registered",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "EMAIL_ALREADY_USED", value = ApiExamples.EMAIL_ALREADY_USED),
                            @ExampleObject(name = "TAX_CODE_ALREADY_USED", value = ApiExamples.TAX_CODE_ALREADY_USED)
                    }))
    @PostMapping(path = "/register", version = "1+")
    public ResponseEntity<CustomerProfile> register(@Validated @RequestBody RegisterRequest request) {
        CustomerEntity saved = authService.register(request.firstName(), request.lastName(), request.email(),
                request.phoneNumber(), request.taxCode(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CustomerProfile(saved.getId(), saved.getFirstName(), saved.getLastName(),
                        saved.getEmail(), saved.getPhoneNumber(), saved.getTaxCode()));
    }

    // FR-AUTH-02: login returns 200 with the access token and the refresh token
    @Operation(summary = "Log in", description = """
            Returns a short-lived JWT access token (5 minutes) and an opaque refresh token (7 days).
            Paste `accessToken` into **Authorize** to call the protected endpoints.
            A new login replaces the previous session: the old refresh token stops working.
            """)
    @ApiResponse(responseCode = "200", description = "Logged in",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = LoginResponse.class),
                    examples = @ExampleObject(value = ApiExamples.LOGIN_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Missing or malformed email/password",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Wrong email or password (same answer for both, on purpose)",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.INVALID_CREDENTIALS)))
    @PostMapping(path = "/login", version = "1+")
    public ResponseEntity<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        return ResponseEntity.ok(toResponse(authService.login(request.email(), request.password())));
    }

    // FR-AUTH-04: public (the access token may already be expired), returns a new token pair
    @Operation(summary = "Refresh the tokens", description = """
            Exchanges the refresh token for a new access token **and** a new refresh token.
            The old refresh token is revoked (rotation): always keep the last one.
            Works even when the access token has already expired; any Authorization header is ignored.
            """)
    @ApiResponse(responseCode = "200", description = "New token pair",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = LoginResponse.class),
                    examples = @ExampleObject(value = ApiExamples.LOGIN_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Missing refresh token",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Refresh token unknown, expired or already used",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.INVALID_REFRESH_TOKEN)))
    @PostMapping(path = "/refresh", version = "1+")
    public ResponseEntity<LoginResponse> refresh(@Validated @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(toResponse(authService.refresh(request.refreshToken())));
    }

    // FR-AUTH-05: public and idempotent, always 204
    @Operation(summary = "Log out", description = """
            Revokes the refresh token, ending the session. Idempotent: an unknown or already revoked
            token still returns 204. The access token stays valid until it expires (at most 5 minutes):
            the client should simply discard it.
            """)
    @ApiResponse(responseCode = "204", description = "Session ended (or already ended)")
    @ApiResponse(responseCode = "400", description = "Missing refresh token",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(path = "/logout", version = "1+")
    public ResponseEntity<Void> logout(@Validated @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private LoginResponse toResponse(AuthTokens tokens) {
        return new LoginResponse(tokens.accessToken(), "Bearer", jwtService.getExpirationSeconds(),
                tokens.refreshToken(), refreshTokenService.getExpirationSeconds());
    }
}
