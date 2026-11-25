package com.bonifybank.bonify.modules.transfer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.SecurityConfig;
import com.bonifybank.bonify.config.WebConfig;
import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
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

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"fromIban\":\"IT60X0542811101000000000001\",\"toIban\":\"IT60X0542811101000000000002\",\"amount\":0}",
            "{\"fromIban\":\"IT60X0542811101000000000001\",\"toIban\":\"IT60X0542811101000000000002\",\"amount\":-1}",
            "{\"fromIban\":\"IT60X0542811101000000000001\",\"toIban\":\"IT60X0542811101000000000002\",\"amount\":1.001}",
            "{\"toIban\":\"IT60X0542811101000000000002\",\"amount\":1}",
            "{\"fromIban\":\"IT60X0542811101000000000001\",\"amount\":1}",
            "{\"fromIban\":\"IT60X0542811101000000000001\",\"toIban\":\"IT60X0542811101000000000002\"}" })
    void invalidBodyIsValidationError(String body) throws Exception {
        mockMvc.perform(send().header("Idempotency-Key", KEY).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(transferService);
    }

    @Test
    void tooLongDescriptionIsValidationError() throws Exception {
        String body = "{\"fromIban\":\"IT60X0542811101000000000001\",\"toIban\":\"IT60X0542811101000000000002\","
                + "\"amount\":1,\"description\":\"" + "d".repeat(141) + "\"}";

        mockMvc.perform(send().header("Idempotency-Key", KEY).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(transferService);
    }

    @Test
    void getTransferReturnsIt() throws Exception {
        TransferResponse transfer = response();
        when(transferService.getTransfer(transfer.id())).thenReturn(transfer);

        mockMvc.perform(get(TRANSFERS + "/" + transfer.id()).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transfer.id().toString()))
                .andExpect(jsonPath("$.fromIban").value("IT60X0542811101000000000001"))
                .andExpect(jsonPath("$.toIban").value("IT60X0542811101000000000002"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.description").value("rent"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    //              code,                              http status
    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(ErrorCodes.SAME_ACCOUNT, 400),
                Arguments.of(ErrorCodes.IDEMPOTENCY_KEY_REUSED, 409),
                Arguments.of(ErrorCodes.INSUFFICIENT_FUNDS, 422),
                Arguments.of(ErrorCodes.ACCOUNT_BLOCKED, 422),
                Arguments.of(ErrorCodes.FORBIDDEN, 403),
                Arguments.of(ErrorCodes.NOT_FOUND, 404));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceErrors")
    void serviceErrorsAreMappedToHttpStatus(ErrorCodes code, int httpStatus) throws Exception {
        when(transferService.sendTransfer(anyString(), any())).thenThrow(new BusinessException(code, "failure"));

        mockMvc.perform(send().header("Idempotency-Key", KEY).content(BODY))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code.name()));
    }

    @Test
    void guestIsUnauthorized() throws Exception {
        mockMvc.perform(post(TRANSFERS).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static MockHttpServletRequestBuilder send() {
        return post(TRANSFERS).with(user("customer").roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON);
    }

    private static TransferResponse response() {
        return new TransferResponse(UUID.randomUUID(), "IT60X0542811101000000000001", "IT60X0542811101000000000002",
                new BigDecimal("150.00"), "rent", "COMPLETED", Instant.now());
    }
}
