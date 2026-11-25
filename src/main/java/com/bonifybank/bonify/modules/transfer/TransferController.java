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

import com.bonifybank.bonify.config.ApiExamples;
import com.bonifybank.bonify.config.OpenApiConfig;
import com.bonifybank.bonify.exception.dto.ErrorResponse;
import com.bonifybank.bonify.modules.transfer.dto.TransferRequest;
import com.bonifybank.bonify.modules.transfer.dto.TransferResponse;
import com.bonifybank.bonify.modules.transfer.dto.TransferResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/transfers")
@Validated
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_TRANSFERS)
public class TransferController {

    private final TransferService transferService;

    // FR-TRF-01 / FR-TRF-02: atomic and idempotent transfer from an own account.
    // 201 for a new transfer, 200 with the original transfer for an idempotent retry.
    @Operation(summary = "Send a transfer", description = """
            Moves money from an own `ACTIVE` account to any other `ACTIVE` EUR account, atomically:
            one `TRANSFER_OUT` movement on the source and one `TRANSFER_IN` on the destination.

            **Idempotency**: generate a new UUID for every transfer and send it in `Idempotency-Key`.
            If the response is lost, retry with the **same key and body**: you get the original transfer
            with 200 and no money is moved twice. The same key with a different body is refused (409).
            """)
    @ApiResponse(responseCode = "201", description = "Transfer completed",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = TransferResponse.class),
                    examples = @ExampleObject(value = ApiExamples.TRANSFER)))
    @ApiResponse(responseCode = "200", description = "Idempotent retry: the original transfer, nothing moved again",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = TransferResponse.class),
                    examples = @ExampleObject(value = ApiExamples.TRANSFER)))
    @ApiResponse(responseCode = "400", description = "Invalid body or Idempotency-Key, same account, different currency",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "SAME_ACCOUNT", value = ApiExamples.SAME_ACCOUNT),
                            @ExampleObject(name = "Invalid Idempotency-Key",
                                    value = ApiExamples.VALIDATION_ERROR_IDEMPOTENCY_KEY),
                            @ExampleObject(name = "Currency mismatch", value = ApiExamples.VALIDATION_ERROR_CURRENCY)
                    }))
    @ApiResponse(responseCode = "403", description = "The source account belongs to another customer",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.FORBIDDEN_TRANSFER_SOURCE)))
    @ApiResponse(responseCode = "404", description = "Unknown source or destination IBAN",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Idempotency-Key already used with a different body",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.IDEMPOTENCY_KEY_REUSED)))
    @ApiResponse(responseCode = "422", description = "Insufficient funds, or one of the accounts is BLOCKED",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "INSUFFICIENT_FUNDS", value = ApiExamples.INSUFFICIENT_FUNDS),
                            @ExampleObject(name = "ACCOUNT_BLOCKED", value = ApiExamples.TRANSFER_ACCOUNT_BLOCKED)
                    }))
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(version = "1+")
    public ResponseEntity<TransferResponse> sendTransfer(
            @Parameter(description = "A new UUID for each transfer; reuse it only to retry the same transfer",
                    example = ApiExamples.IDEMPOTENCY_KEY)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Validated @RequestBody TransferRequest request) {
        TransferResult result = transferService.sendTransfer(idempotencyKey, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.transfer());
    }

    // FR-TRF-03: sender, receiver or admin (checked by the service)
    @Operation(summary = "Get a transfer", description = """
            Transfer detail. Visible to the owner of the source account, the owner of the destination
            account and the admin. The id is in the transfer response and in the `transferId` of the movements.
            """)
    @ApiResponse(responseCode = "200", description = "Transfer detail",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = TransferResponse.class),
                    examples = @ExampleObject(value = ApiExamples.TRANSFER)))
    @ApiResponse(responseCode = "400", description = "The id is not a UUID",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is neither sender, receiver nor admin",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Unknown transfer",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.TRANSFER_NOT_FOUND)))
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @GetMapping(path = "/{id}", version = "1+")
    public ResponseEntity<TransferResponse> getTransfer(
            @Parameter(description = "Transfer id", example = ApiExamples.TRANSFER_ID) @PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }
}
