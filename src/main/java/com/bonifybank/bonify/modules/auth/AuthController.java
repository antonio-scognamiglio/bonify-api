package com.bonifybank.bonify.modules.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bonifybank.bonify.modules.auth.dto.CustomerProfile;
import com.bonifybank.bonify.modules.auth.dto.LoginRequest;
import com.bonifybank.bonify.modules.auth.dto.LoginResponse;
import com.bonifybank.bonify.modules.auth.dto.RefreshTokenRequest;
import com.bonifybank.bonify.modules.auth.dto.RegisterRequest;
import com.bonifybank.bonify.modules.customer.CustomerEntity;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@Validated
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // FR-AUTH-01: public registration, returns 201 with the profile (no password)
    @PostMapping(path = "/register", version = "1+")
    public ResponseEntity<CustomerProfile> register(@Validated @RequestBody RegisterRequest request) {
        CustomerEntity saved = authService.register(request.firstName(), request.lastName(), request.email(),
                request.phoneNumber(), request.taxCode(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CustomerProfile(saved.getId(), saved.getFirstName(), saved.getLastName(),
                        saved.getEmail(), saved.getPhoneNumber(), saved.getTaxCode()));
    }

    // FR-AUTH-02: login returns 200 with the access token and the refresh token
    @PostMapping(path = "/login", version = "1+")
    public ResponseEntity<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        return ResponseEntity.ok(toResponse(authService.login(request.email(), request.password())));
    }

    // FR-AUTH-04: public (the access token may already be expired), returns a new token pair
    @PostMapping(path = "/refresh", version = "1+")
    public ResponseEntity<LoginResponse> refresh(@Validated @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(toResponse(authService.refresh(request.refreshToken())));
    }

    // FR-AUTH-05: public and idempotent, always 204
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
