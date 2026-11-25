package com.bonifybank.bonify.modules.account;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bonifybank.bonify.modules.account.dto.AccountResponse;
import com.bonifybank.bonify.modules.account.dto.CashOperationRequest;
import com.bonifybank.bonify.modules.account.dto.UpdateAccountStatusRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/accounts")
@Validated
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // FR-ACC-01: no body, opens an EUR account owned by the caller, returns 201
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(version = "1+")
    public ResponseEntity<AccountResponse> openAccount() {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.openAccount());
    }

    // FR-ACC-02: all the caller's accounts
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping(version = "1+")
    public ResponseEntity<List<AccountResponse>> listMyAccounts() {
        return ResponseEntity.ok(accountService.listMyAccounts());
    }

    // FR-ACC-03: owner or admin (ownership is checked by the service)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @GetMapping(path = "/{iban}", version = "1+")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String iban) {
        return ResponseEntity.ok(accountService.getAccount(iban));
    }

    // FR-ACC-04: owner only, account must be ACTIVE
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(path = "/{iban}/deposit", version = "1+")
    public ResponseEntity<AccountResponse> deposit(@PathVariable String iban,
            @Validated @RequestBody CashOperationRequest request) {
        return ResponseEntity.ok(accountService.deposit(iban, request));
    }

    // FR-ACC-05: owner only, account must be ACTIVE, balance cannot go negative
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(path = "/{iban}/withdraw", version = "1+")
    public ResponseEntity<AccountResponse> withdraw(@PathVariable String iban,
            @Validated @RequestBody CashOperationRequest request) {
        return ResponseEntity.ok(accountService.withdraw(iban, request));
    }

    // FR-ACC-06: block or unblock an account
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping(path = "/{iban}/status", version = "1+")
    public ResponseEntity<AccountResponse> updateStatus(@PathVariable String iban,
            @Validated @RequestBody UpdateAccountStatusRequest request) {
        return ResponseEntity.ok(accountService.updateStatus(iban, request));
    }
}
