package com.bonifybank.bonify.modules.transaction;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bonifybank.bonify.common.dto.PagedResponse;
import com.bonifybank.bonify.modules.transaction.dto.TransactionResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/accounts/{iban}/transactions")
// No class-level @Validated: Spring MVC validates the @Min/@Max parameters itself and raises
// HandlerMethodValidationException (400); @Validated would switch to AOP validation instead
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // FR-TRX-01: paged history, newest first, optional inclusive date range (owner or admin)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @GetMapping(version = "1+")
    public ResponseEntity<PagedResponse<TransactionResponse>> listTransactions(
            @PathVariable String iban,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(transactionService.listTransactions(iban, page, size, from, to));
    }
}
