package com.bonifybank.bonify.modules.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    // TODO (DTOs): take @Valid @RequestBody RegisterRequest, call
    //  authService.register(firstName, lastName, email, password)
    //  and return 201 Created with the customer profile DTO (no password).
    @PostMapping(path = "/register", version = "1+")
    public ResponseEntity<Void> register() {
        return notImplemented();
    }

    // TODO (DTOs): take @Valid @RequestBody LoginRequest, call authService.login(email, password)
    //  and return 200 OK with { accessToken, tokenType: "Bearer", expiresIn: jwtService.getExpirationSeconds() }.
    @PostMapping(path = "/login", version = "1+")
    public ResponseEntity<Void> login() {
        return notImplemented();
    }

    private static ResponseEntity<Void> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
