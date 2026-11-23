package com.bonifybank.bonify.modules.customer;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    @GetMapping(path = "/me", version = "1+")
    public ResponseEntity<Void> getMe() {
        return notImplemented();
    }

    @PatchMapping(path = "/me", version = "1+")
    public ResponseEntity<Void> updateMe() {
        return notImplemented();
    }

    @PutMapping(path = "/me/password", version = "1+")
    public ResponseEntity<Void> changeMyPassword() {
        return notImplemented();
    }

    @GetMapping(version = "1+")
    public ResponseEntity<Void> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notImplemented();
    }

    @PatchMapping(path = "/{id}", version = "1+")
    public ResponseEntity<Void> updateCustomer(@PathVariable UUID id) {
        return notImplemented();
    }

    private static ResponseEntity<Void> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
