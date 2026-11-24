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

    // FR-AUTH-01: public registration, returns 201 with the profile (no password)
    @PostMapping(path = "/register", version = "1+")
    public ResponseEntity<CustomerProfile> register(@Validated @RequestBody RegisterRequest request) {
        CustomerEntity saved = authService.register(
                request.firstName(), request.lastName(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CustomerProfile(
                        saved.getId(), saved.getFirstName(), saved.getLastName(), saved.getEmail()));
    }

    // FR-AUTH-02: login returns 200 with the access token
    @PostMapping(path = "/login", version = "1+")
    public ResponseEntity<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds()));
    }
}
