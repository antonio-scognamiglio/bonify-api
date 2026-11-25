package com.bonifybank.bonify.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Stable JSON shape of every paged endpoint (FR-CUS-04, FR-TRX-01). */
class PagedResponseTests {

    @Test
    void fromCopiesContentAndPagingData() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PagedResponse<String> response = PagedResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.last()).isFalse();
    }

    @Test
    void fromWithMapperMapsContentAndCopiesPagingData() {
        PageImpl<Integer> page = new PageImpl<>(List.of(5), PageRequest.of(2, 2), 5);

        PagedResponse<String> response = PagedResponse.from(page, n -> "n" + n);

        assertThat(response.content()).containsExactly("n5");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.last()).isTrue();
    }
}
