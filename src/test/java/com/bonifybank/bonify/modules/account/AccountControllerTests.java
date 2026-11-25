package com.bonifybank.bonify.modules.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.SecurityConfig;
import com.bonifybank.bonify.config.WebConfig;
import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.account.dto.AccountResponse;
import com.bonifybank.bonify.modules.account.dto.CashOperationRequest;
import com.bonifybank.bonify.modules.account.dto.UpdateAccountStatusRequest;

/**
 * FR-ACC-01..06 at the web layer: status codes, body validation and the mapping of
 * service errors to RFC 7807 responses. Role rules are covered by RoutePermissionsTests.
 */
@WebMvcTest(AccountController.class)
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class })
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class AccountControllerTests {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String IBAN = "IT60X0542811101000000000001";
    private static final String DEPOSIT = ACCOUNTS + "/" + IBAN + "/deposit";
    private static final String WITHDRAW = ACCOUNTS + "/" + IBAN + "/withdraw";
    private static final String STATUS = ACCOUNTS + "/" + IBAN + "/status";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AccountService accountService;

    @Test
    void openAccountReturnsCreatedAccount() throws Exception {
        when(accountService.openAccount()).thenReturn(account());

        mockMvc.perform(post(ACCOUNTS).with(customer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iban").value(IBAN))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void listReturnsMyAccounts() throws Exception {
        when(accountService.listMyAccounts()).thenReturn(List.of(account(), account()));

        mockMvc.perform(get(ACCOUNTS).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].iban").value(IBAN));
    }

    @Test
    void getAccountReturnsIt() throws Exception {
        when(accountService.getAccount(IBAN)).thenReturn(account());

        mockMvc.perform(get(ACCOUNTS + "/" + IBAN).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(IBAN))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void depositPassesIbanAmountAndDescriptionToTheService() throws Exception {
        when(accountService.deposit(anyString(), any())).thenReturn(account());

        mockMvc.perform(json(post(DEPOSIT), "{\"amount\":50.25,\"description\":\"salary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(IBAN));

        verify(accountService).deposit(eq(IBAN), eq(new CashOperationRequest(new BigDecimal("50.25"), "salary")));
    }

    @Test
    void withdrawPassesIbanAmountAndDescriptionToTheService() throws Exception {
        when(accountService.withdraw(anyString(), any())).thenReturn(account());

        mockMvc.perform(json(post(WITHDRAW), "{\"amount\":10,\"description\":\"atm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(IBAN));

        verify(accountService).withdraw(eq(IBAN), eq(new CashOperationRequest(new BigDecimal("10"), "atm")));
    }

    static Stream<Arguments> invalidCashRequests() {
        String[] bodies = { "{\"description\":\"x\"}", "{\"amount\":0}", "{\"amount\":-5}", "{\"amount\":10.001}",
                "{\"amount\":10,\"description\":\"" + "d".repeat(141) + "\"}" };
        return Stream.of(DEPOSIT, WITHDRAW).flatMap(path -> Stream.of(bodies).map(body -> Arguments.of(path, body)));
    }

    @ParameterizedTest
    @MethodSource("invalidCashRequests")
    void invalidCashBodiesAreValidationErrors(String path, String body) throws Exception {
        mockMvc.perform(json(post(path), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(accountService);
    }

    @ParameterizedTest
    @ValueSource(strings = { DEPOSIT, WITHDRAW })
    void missingBodyIsValidationError(String path) throws Exception {
        mockMvc.perform(post(path).with(customer()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(accountService);
    }

    @ParameterizedTest
    @ValueSource(strings = { DEPOSIT, WITHDRAW })
    void malformedJsonIsValidationError(String path) throws Exception {
        mockMvc.perform(json(post(path), "{\"amount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(accountService);
    }

    @Test
    void insufficientFundsIsRfc7807Body() throws Exception {
        when(accountService.withdraw(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCodes.INSUFFICIENT_FUNDS, "Balance is too low"));

        mockMvc.perform(json(post(WITHDRAW), "{\"amount\":10}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://bonify.example/errors/insufficient-funds"))
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Balance is too low"))
                .andExpect(jsonPath("$.instance").value(WITHDRAW))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void accountBlockedIs422() throws Exception {
        when(accountService.deposit(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCodes.ACCOUNT_BLOCKED, "Account is blocked"));

        mockMvc.perform(json(post(DEPOSIT), "{\"amount\":10}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    }

    @Test
    void forbiddenFromServiceIs403() throws Exception {
        when(accountService.getAccount(anyString()))
                .thenThrow(new BusinessException(ErrorCodes.FORBIDDEN, "Not your account"));

        mockMvc.perform(get(ACCOUNTS + "/" + IBAN).with(customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void notFoundFromServiceIs404() throws Exception {
        when(accountService.getAccount(anyString()))
                .thenThrow(new BusinessException(ErrorCodes.NOT_FOUND, "Account not found"));

        mockMvc.perform(get(ACCOUNTS + "/" + IBAN).with(customer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void adminUpdatesStatus() throws Exception {
        when(accountService.updateStatus(anyString(), any())).thenReturn(account());

        mockMvc.perform(patch(STATUS).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk());

        verify(accountService).updateStatus(eq(IBAN), eq(new UpdateAccountStatusRequest("BLOCKED")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"status\":\"\"}", "{\"status\":\"   \"}" })
    void blankOrMissingStatusIsValidationError(String body) throws Exception {
        mockMvc.perform(patch(STATUS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(accountService);
    }

    @Test
    void unknownStatusRejectedByServiceIs400() throws Exception {
        when(accountService.updateStatus(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCodes.VALIDATION_ERROR, "Unknown status"));

        mockMvc.perform(patch(STATUS).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonJsonContentTypeIs415() throws Exception {
        mockMvc.perform(post(DEPOSIT).with(customer()).contentType(MediaType.TEXT_PLAIN).content("amount=10"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unmappedMethodIs405WithAllowHeader() throws Exception {
        mockMvc.perform(delete(ACCOUNTS).with(customer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.with(customer()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor customer() {
        return user("customer").roles("CUSTOMER");
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private static AccountResponse account() {
        return new AccountResponse(1L, IBAN, "EUR", BigDecimal.ZERO, "ACTIVE", UUID.randomUUID(),
                Instant.now());
    }
}
