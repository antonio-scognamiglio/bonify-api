package com.bonifybank.bonify.modules.transfer;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bonifybank.bonify.modules.transfer.dto.TransferRequest;
import com.bonifybank.bonify.modules.transfer.dto.TransferResponse;
import com.bonifybank.bonify.modules.transfer.dto.TransferResult;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/transfers")
@Validated
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    // FR-TRF-01 / FR-TRF-02: atomic and idempotent transfer from an own account.
    // 201 for a new transfer, 200 with the original transfer for an idempotent retry.
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(version = "1+")
    public ResponseEntity<TransferResponse> sendTransfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Validated @RequestBody TransferRequest request) {
        TransferResult result = transferService.sendTransfer(idempotencyKey, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.transfer());
    }

    // FR-TRF-03: sender, receiver or admin (checked by the service)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @GetMapping(path = "/{id}", version = "1+")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }
}
