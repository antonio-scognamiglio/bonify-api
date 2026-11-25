package com.bonifybank.bonify.modules.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bonifybank.bonify.common.dto.PagedResponse;
import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.SecurityConfig;
import com.bonifybank.bonify.config.WebConfig;
import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.transaction.dto.TransactionResponse;

/**
 * FR-TRX-01 query parameters: page >= 0, 1 <= size <= 100, ISO dates. Invalid values are 400, not 500.
 */
@WebMvcTest(TransactionController.class)
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class })
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class TransactionControllerTests {

    private static final String IBAN = "IT60X0542811101000000000001";
    private static final String HISTORY = "/api/v1/accounts/" + IBAN + "/transactions";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransactionService transactionService;

    @ParameterizedTest
    @ValueSource(strings = { "size=101", "size=0", "page=-1", "from=yesterday", "to=2026-13-01" })
    void invalidQueryIsValidationError(String query) throws Exception {
        mockMvc.perform(get(HISTORY + "?" + query).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "size=100", "size=1&page=0", "from=2026-01-01&to=2026-12-31" })
    void validQueryReachesService(String query) throws Exception {
        mockMvc.perform(get(HISTORY + "?" + query).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void responseHasTheTransactionAndPagingFields() throws Exception {
        TransactionResponse line = new TransactionResponse(1L, "TRANSFER_OUT", new BigDecimal("25.50"),
                new BigDecimal("74.50"), "rent", UUID.randomUUID(), Instant.now());
        when(transactionService.listTransactions(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PagedResponse<>(List.of(line), 0, 20, 1, 1, true));

        mockMvc.perform(get(HISTORY).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(line.id().toString()))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.content[0].amount").value(25.50))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(74.50))
                .andExpect(jsonPath("$.content[0].description").value("rent"))
                .andExpect(jsonPath("$.content[0].transferId").value(line.transferId().toString()))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void defaultsReachTheService() throws Exception {
        mockMvc.perform(get(HISTORY).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk());

        verify(transactionService).listTransactions(IBAN, 0, 20, null, null);
    }

    @Test
    void parsedDatesReachTheService() throws Exception {
        mockMvc.perform(get(HISTORY + "?page=3&size=50&from=2026-01-01&to=2026-02-28")
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk());

        verify(transactionService).listTransactions(IBAN, 3, 50, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));
    }

    //              code,                          http status
    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(ErrorCodes.VALIDATION_ERROR, 400),
                Arguments.of(ErrorCodes.FORBIDDEN, 403),
                Arguments.of(ErrorCodes.NOT_FOUND, 404));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceErrors")
    void serviceErrorsAreMappedToHttpStatus(ErrorCodes code, int httpStatus) throws Exception {
        when(transactionService.listTransactions(anyString(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new BusinessException(code, "failure"));

        mockMvc.perform(get(HISTORY).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code.name()));
    }
}
