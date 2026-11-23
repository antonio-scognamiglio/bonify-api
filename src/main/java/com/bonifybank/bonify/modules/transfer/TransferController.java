package com.bonifybank.bonify.modules.transfer;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    @PostMapping(version = "1+")
    public ResponseEntity<Void> sendTransfer(@RequestHeader("Idempotency-Key") String idempotencyKey) {
        return notImplemented();
    }

    @GetMapping(path = "/{id}", version = "1+")
    public ResponseEntity<Void> getTransfer(@PathVariable UUID id) {
        return notImplemented();
    }

    private static ResponseEntity<Void> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
