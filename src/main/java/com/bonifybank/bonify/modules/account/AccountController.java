package com.bonifybank.bonify.modules.account;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(version = "1+")
    public ResponseEntity<Void> openAccount() {
        return notImplemented();
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping(version = "1+")
    public ResponseEntity<Void> listMyAccounts() {
        return notImplemented();
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @GetMapping(path = "/{iban}", version = "1+")
    public ResponseEntity<Void> getAccount(@PathVariable String iban) {
        return notImplemented();
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(path = "/{iban}/deposit", version = "1+")
    public ResponseEntity<Void> deposit(@PathVariable String iban) {
        return notImplemented();
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(path = "/{iban}/withdraw", version = "1+")
    public ResponseEntity<Void> withdraw(@PathVariable String iban) {
        return notImplemented();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping(path = "/{iban}/status", version = "1+")
    public ResponseEntity<Void> updateStatus(@PathVariable String iban) {
        return notImplemented();
    }

    private static ResponseEntity<Void> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
