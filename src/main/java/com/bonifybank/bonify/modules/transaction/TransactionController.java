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
import com.bonifybank.bonify.config.ApiExamples;
import com.bonifybank.bonify.config.ApiSchemas;
import com.bonifybank.bonify.config.OpenApiConfig;
import com.bonifybank.bonify.exception.dto.ErrorResponse;
import com.bonifybank.bonify.modules.transaction.dto.TransactionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/accounts/{iban}/transactions")
// No class-level @Validated: Spring MVC validates the @Min/@Max parameters itself and raises
// HandlerMethodValidationException (400); @Validated would switch to AOP validation instead
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_TRANSACTIONS)
public class TransactionController {

    private final TransactionService transactionService;

    // FR-TRX-01: paged history, newest first, optional inclusive date range (owner or admin)
    @Operation(summary = "Transaction history", description = """
            Paged movements of an account, newest first. Owner or admin only.
            `from` and `to` are optional and inclusive (whole days, UTC); `from` must not be after `to`.
            `transferId` is set only on `TRANSFER_IN` / `TRANSFER_OUT` movements:
            use it with `GET /api/v1/transfers/{id}`.
            """)
    @ApiResponse(responseCode = "200", description = "One page of movements",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = ApiSchemas.TransactionPage.class),
                    examples = @ExampleObject(value = ApiExamples.TRANSACTION_PAGE)))
    @ApiResponse(responseCode = "400", description = "Invalid page, size or dates",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.VALIDATION_ERROR_DATE_RANGE)))
    @ApiResponse(responseCode = "403", description = "The account belongs to another customer",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.FORBIDDEN_ACCOUNT)))
    @ApiResponse(responseCode = "404", description = "Unknown IBAN",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.ACCOUNT_NOT_FOUND)))
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @GetMapping(version = "1+")
    public ResponseEntity<PagedResponse<TransactionResponse>> listTransactions(
            @Parameter(description = "IBAN of the account", example = ApiExamples.IBAN)
            @PathVariable String iban,
            @Parameter(description = "Page number, from 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, from 1 to 100", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "First day included (YYYY-MM-DD)", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day included (YYYY-MM-DD)", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(transactionService.listTransactions(iban, page, size, from, to));
    }
}
