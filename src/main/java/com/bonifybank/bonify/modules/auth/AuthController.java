package com.bonifybank.bonify.modules.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping(path = "/register", version = "1+")
    public ResponseEntity<Void> register() {
        return notImplemented();
    }

    @PostMapping(path = "/login", version = "1+")
    public ResponseEntity<Void> login() {
        return notImplemented();
    }

    private static ResponseEntity<Void> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
