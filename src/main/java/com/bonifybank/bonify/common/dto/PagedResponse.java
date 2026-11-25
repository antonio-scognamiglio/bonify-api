package com.bonifybank.bonify.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable JSON shape for every paged endpoint (customers, transaction history).
 * Spring Data discourages serializing PageImpl as-is, so pages are always mapped to this record.
 */
@Schema(description = "One page of results, newest first")
public record PagedResponse<T>(
        @Schema(description = "Items of this page")
        List<T> content,
        @Schema(description = "Page number, from 0", example = "0")
        int page,
        @Schema(description = "Requested page size", example = "20")
        int size,
        @Schema(description = "Items across all pages", example = "3")
        long totalElements,
        @Schema(description = "Number of pages", example = "1")
        int totalPages,
        @Schema(description = "True on the last page", example = "true")
        boolean last
) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
