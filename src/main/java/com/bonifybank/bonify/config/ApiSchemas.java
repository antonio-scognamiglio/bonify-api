package com.bonifybank.bonify.config;

import java.util.List;

import com.bonifybank.bonify.common.dto.PagedResponse;
import com.bonifybank.bonify.modules.customer.dto.CustomerResponse;
import com.bonifybank.bonify.modules.transaction.dto.TransactionResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation-only schemas. An @ApiResponse cannot reference a generic type such as
 * PagedResponse&lt;CustomerResponse&gt;, and once a response is annotated springdoc no longer infers
 * it from the return type. These records mirror {@link PagedResponse} with a concrete item type;
 * they are never instantiated, so keep their fields aligned with PagedResponse.
 */
public final class ApiSchemas {

    private ApiSchemas() {
    }

    @Schema(name = "CustomerPage", description = "One page of customers, newest first")
    public record CustomerPage(
            List<CustomerResponse> content,
            @Schema(description = "Page number, from 0", example = "0") int page,
            @Schema(description = "Requested page size", example = "20") int size,
            @Schema(description = "Items across all pages", example = "1") long totalElements,
            @Schema(description = "Number of pages", example = "1") int totalPages,
            @Schema(description = "True on the last page", example = "true") boolean last) {
    }

    @Schema(name = "TransactionPage", description = "One page of movements, newest first")
    public record TransactionPage(
            List<TransactionResponse> content,
            @Schema(description = "Page number, from 0", example = "0") int page,
            @Schema(description = "Requested page size", example = "20") int size,
            @Schema(description = "Items across all pages", example = "3") long totalElements,
            @Schema(description = "Number of pages", example = "1") int totalPages,
            @Schema(description = "True on the last page", example = "true") boolean last) {
    }
}
