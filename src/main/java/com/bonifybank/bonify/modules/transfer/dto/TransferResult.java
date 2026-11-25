package com.bonifybank.bonify.modules.transfer.dto;

/**
 * Outcome of a transfer request (FR-TRF-02): {@code created} is false for an idempotent retry,
 * so the controller answers 201 for a new transfer and 200 for a replay. Not exposed to clients.
 */
public record TransferResult(TransferResponse transfer, boolean created) {
}
