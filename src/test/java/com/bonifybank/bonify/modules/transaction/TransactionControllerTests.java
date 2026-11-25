package com.bonifybank.bonify.modules.transaction;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.SecurityConfig;
import com.bonifybank.bonify.config.WebConfig;

/**
 * FR-TRX-01 query parameters: page >= 0, 1 <= size <= 100, ISO dates. Invalid values are 400, not 500.
 */
@WebMvcTest(TransactionController.class)
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class })
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class TransactionControllerTests {

    private static final String HISTORY = "/api/v1/accounts/IT60X0542811101000000000001/transactions";

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
}
