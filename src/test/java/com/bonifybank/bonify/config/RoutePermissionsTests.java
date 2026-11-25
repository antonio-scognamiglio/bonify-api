package com.bonifybank.bonify.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bonifybank.bonify.modules.account.AccountController;
import com.bonifybank.bonify.modules.account.AccountService;
import com.bonifybank.bonify.modules.auth.AuthController;
import com.bonifybank.bonify.modules.auth.AuthService;
import com.bonifybank.bonify.modules.auth.AuthTokens;
import com.bonifybank.bonify.modules.auth.JwtService;
import com.bonifybank.bonify.modules.auth.RefreshTokenService;
import com.bonifybank.bonify.modules.customer.CustomerController;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerService;
import com.bonifybank.bonify.modules.transaction.TransactionController;
import com.bonifybank.bonify.modules.transaction.TransactionService;
import com.bonifybank.bonify.modules.transfer.TransferController;
import com.bonifybank.bonify.modules.transfer.TransferService;
import com.bonifybank.bonify.modules.transfer.dto.TransferResult;

/**
 * Permission matrix (functional-analysis.md, endpoint table).
 * 2xx = the request passed security and reached the controller (services are mocked).
 * 401 = unauthenticated user, 403 = authenticated but without the required role.
 */
@WebMvcTest({ AuthController.class, CustomerController.class, AccountController.class,
        TransactionController.class, TransferController.class })
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class })
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class RoutePermissionsTests {

    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int REGISTERED = 201;
    private static final int LOGIN_OK = 200;
    private static final int NO_CONTENT = 204;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    private static final String IBAN = "IT60X0542811101000000123456";
    private static final String UUID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    RefreshTokenService refreshTokenService;

    @MockitoBean
    CustomerService customerService;

    @MockitoBean
    AccountService accountService;

    @MockitoBean
    TransactionService transactionService;

    @MockitoBean
    TransferService transferService;

    @BeforeEach
    void stubAuthService() {
        when(authService.register(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CustomerEntity.builder().id(java.util.UUID.randomUUID()).build());
        AuthTokens tokens = new AuthTokens("test-token", "test-refresh-token");
        when(authService.login(anyString(), anyString())).thenReturn(tokens);
        when(authService.refresh(anyString())).thenReturn(tokens);
        // The controller maps the page: a null Page would fail with 500
        when(customerService.listCustomers(anyInt(), anyInt())).thenReturn(Page.empty());
        when(transferService.sendTransfer(anyString(), any())).thenReturn(new TransferResult(null, true));
    }

    //                    method,           path,                                     guest,        customer,  admin
    static Stream<Arguments> routes() {
        return Stream.of(
                route(HttpMethod.POST,  "/api/v1/auth/register",                      REGISTERED,   REGISTERED, REGISTERED),
                route(HttpMethod.POST,  "/api/v1/auth/login",                         LOGIN_OK,     LOGIN_OK,   LOGIN_OK),
                route(HttpMethod.POST,  "/api/v1/auth/refresh",                       LOGIN_OK,     LOGIN_OK,   LOGIN_OK),
                route(HttpMethod.POST,  "/api/v1/auth/logout",                        NO_CONTENT,   NO_CONTENT, NO_CONTENT),
                route(HttpMethod.GET,   "/api/v1/customers/me",                       UNAUTHORIZED, OK,        OK),
                route(HttpMethod.PATCH, "/api/v1/customers/me",                       UNAUTHORIZED, OK,        OK),
                route(HttpMethod.PUT,   "/api/v1/customers/me/password",              UNAUTHORIZED, NO_CONTENT, NO_CONTENT),
                route(HttpMethod.GET,   "/api/v1/customers",                          UNAUTHORIZED, FORBIDDEN, OK),
                route(HttpMethod.POST,  "/api/v1/accounts",                           UNAUTHORIZED, CREATED,   FORBIDDEN),
                route(HttpMethod.GET,   "/api/v1/accounts",                           UNAUTHORIZED, OK,        FORBIDDEN),
                route(HttpMethod.GET,   "/api/v1/accounts/" + IBAN,                   UNAUTHORIZED, OK,        OK),
                route(HttpMethod.POST,  "/api/v1/accounts/" + IBAN + "/deposit",      UNAUTHORIZED, OK,        FORBIDDEN),
                route(HttpMethod.POST,  "/api/v1/accounts/" + IBAN + "/withdraw",     UNAUTHORIZED, OK,        FORBIDDEN),
                route(HttpMethod.GET,   "/api/v1/accounts/" + IBAN + "/transactions", UNAUTHORIZED, OK,        OK),
                route(HttpMethod.PATCH, "/api/v1/accounts/" + IBAN + "/status",       UNAUTHORIZED, FORBIDDEN, OK),
                route(HttpMethod.POST,  "/api/v1/transfers",                          UNAUTHORIZED, CREATED,   FORBIDDEN),
                route(HttpMethod.GET,   "/api/v1/transfers/" + UUID,                  UNAUTHORIZED, OK,        OK));
    }

    private static Arguments route(HttpMethod method, String path, int guest, int customer, int admin) {
        return Arguments.of(method, path, guest, customer, admin);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routes")
    void guest(HttpMethod method, String path, int guest, int customer, int admin) throws Exception {
        mockMvc.perform(build(method, path)).andExpect(status().is(guest));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routes")
    void customer(HttpMethod method, String path, int guest, int customer, int admin) throws Exception {
        mockMvc.perform(build(method, path).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().is(customer));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routes")
    void admin(HttpMethod method, String path, int guest, int customer, int admin) throws Exception {
        mockMvc.perform(build(method, path).with(user("admin").roles("ADMIN")))
                .andExpect(status().is(admin));
    }

    private static MockHttpServletRequestBuilder build(HttpMethod method, String path) {
        MockHttpServletRequestBuilder builder = request(method, path).header("Idempotency-Key", UUID);
        String body = bodyFor(method, path);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return builder;
    }

    // A valid body for every route that needs one, so that validation never hides the security outcome
    private static String bodyFor(HttpMethod method, String path) {
        if (path.endsWith("/auth/register")) {
            return "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john@example.com\","
                    + "\"phoneNumber\":\"+393401234567\",\"taxCode\":\"DOEJHN90A01H501X\",\"password\":\"password123\"}";
        }
        if (path.endsWith("/auth/login")) {
            return "{\"email\":\"john@example.com\",\"password\":\"password123\"}";
        }
        if (path.startsWith("/api/v1/auth/")) {
            return "{\"refreshToken\":\"test-refresh-token\"}";
        }
        if (path.endsWith("/me/password")) {
            return "{\"currentPassword\":\"password123\",\"newPassword\":\"newPassword123\"}";
        }
        if (method == HttpMethod.PATCH && path.equals("/api/v1/customers/me")) {
            return "{\"firstName\":\"Jane\"}";
        }
        if (path.endsWith("/deposit") || path.endsWith("/withdraw")) {
            return "{\"amount\":10.00}";
        }
        if (path.endsWith("/status")) {
            return "{\"status\":\"BLOCKED\"}";
        }
        if (method == HttpMethod.POST && path.equals("/api/v1/transfers")) {
            return "{\"fromIban\":\"" + IBAN + "\",\"toIban\":\"IT60X0542811101000000654321\",\"amount\":10.00}";
        }
        return null;
    }
}
