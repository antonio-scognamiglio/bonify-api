package com.bonifybank.bonify.modules.transfer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.SecurityConfig;
import com.bonifybank.bonify.config.WebConfig;
import com.bonifybank.bonify.modules.transfer.dto.TransferResponse;
import com.bonifybank.bonify.modules.transfer.dto.TransferResult;

/**
 * FR-TRF-01 / FR-TRF-02 at the web layer: status codes for new and replayed transfers,
 * and malformed requests mapped to 400 VALIDATION_ERROR instead of 500.
 */
@WebMvcTest(TransferController.class)
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class })
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class TransferControllerTests {

    private static final String TRANSFERS = "/api/v1/transfers";
    private static final String KEY = "6f1c2a8e-3b4d-4e5f-9a10-1b2c3d4e5f60";
    private static final String BODY = "{\"fromIban\":\"IT60X0542811101000000000001\","
            + "\"toIban\":\"IT60X0542811101000000000002\",\"amount\":150.00,\"description\":\"rent\"}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransferService transferService;

    @Test
    void newTransferIsCreated() throws Exception {
        when(transferService.sendTransfer(anyString(), any())).thenReturn(new TransferResult(response(), true));

        mockMvc.perform(send().header("Idempotency-Key", KEY).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromIban").value("IT60X0542811101000000000001"))
                .andExpect(jsonPath("$.toIban").value("IT60X0542811101000000000002"));
    }

    @Test
    void idempotentRetryIsOk() throws Exception {
        when(transferService.sendTransfer(anyString(), any())).thenReturn(new TransferResult(response(), false));

        mockMvc.perform(send().header("Idempotency-Key", KEY).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void missingIdempotencyKeyIsValidationError() throws Exception {
        mockMvc.perform(send().content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Missing required header: Idempotency-Key"));
    }

    @Test
    void missingBodyIsValidationError() throws Exception {
        mockMvc.perform(send().header("Idempotency-Key", KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformedJsonIsValidationError() throws Exception {
        mockMvc.perform(send().header("Idempotency-Key", KEY).content("{\"amount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void oldFieldNamesAreRejected() throws Exception {
        String oldBody = "{\"sourceIban\":\"IT60X0542811101000000000001\","
                + "\"targetIban\":\"IT60X0542811101000000000002\",\"amount\":1.00}";

        mockMvc.perform(send().header("Idempotency-Key", KEY).content(oldBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonUuidTransferIdIsValidationError() throws Exception {
        mockMvc.perform(get(TRANSFERS + "/not-a-uuid").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Invalid value for 'id'"));
    }

    private static MockHttpServletRequestBuilder send() {
        return post(TRANSFERS).with(user("customer").roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON);
    }

    private static TransferResponse response() {
        return new TransferResponse(UUID.randomUUID(), "IT60X0542811101000000000001", "IT60X0542811101000000000002",
                new BigDecimal("150.00"), "rent", "COMPLETED", Instant.now());
    }
}
