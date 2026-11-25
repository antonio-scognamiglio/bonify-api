package com.bonifybank.bonify.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bonifybank.bonify.modules.account.AccountController;
import com.bonifybank.bonify.modules.auth.AuthController;
import com.bonifybank.bonify.modules.auth.AuthService;
import com.bonifybank.bonify.modules.auth.AuthTokens;
import com.bonifybank.bonify.modules.auth.JwtService;
import com.bonifybank.bonify.modules.auth.RefreshTokenService;
import com.bonifybank.bonify.modules.customer.CustomerController;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.transaction.TransactionController;
import com.bonifybank.bonify.modules.transfer.TransferController;

/**
 * Permission matrix (functional-analysis.md, endpoint table).
 * 501 = the request passed security and reached the controller (not implemented yet).
 * 401 = unauthenticated user, 403 = authenticated but without the required role.
 */
@WebMvcTest({ AuthController.class, CustomerController.class, AccountController.class,
        TransactionController.class, TransferController.class })
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class })
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class RoutePermissionsTests {

    private static final int OK = 501;
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

    @BeforeEach
    void stubAuthService() {
        when(authService.register(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CustomerEntity.builder().id(java.util.UUID.randomUUID()).build());
        AuthTokens tokens = new AuthTokens("test-token", "test-refresh-token");
        when(authService.login(anyString(), anyString())).thenReturn(tokens);
        when(authService.refresh(anyString())).thenReturn(tokens);
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
                route(HttpMethod.PUT,   "/api/v1/customers/me/password",              UNAUTHORIZED, OK,        OK),
                route(HttpMethod.GET,   "/api/v1/customers",                          UNAUTHORIZED, FORBIDDEN, OK),
                route(HttpMethod.PATCH, "/api/v1/customers/" + UUID,                  UNAUTHORIZED, FORBIDDEN, OK),
                route(HttpMethod.POST,  "/api/v1/accounts",                           UNAUTHORIZED, OK,        FORBIDDEN),
                route(HttpMethod.GET,   "/api/v1/accounts",                           UNAUTHORIZED, OK,        FORBIDDEN),
                route(HttpMethod.GET,   "/api/v1/accounts/" + IBAN,                   UNAUTHORIZED, OK,        OK),
                route(HttpMethod.POST,  "/api/v1/accounts/" + IBAN + "/deposit",      UNAUTHORIZED, OK,        FORBIDDEN),
                route(HttpMethod.POST,  "/api/v1/accounts/" + IBAN + "/withdraw",     UNAUTHORIZED, OK,        FORBIDDEN),
                route(HttpMethod.GET,   "/api/v1/accounts/" + IBAN + "/transactions", UNAUTHORIZED, OK,        OK),
                route(HttpMethod.PATCH, "/api/v1/accounts/" + IBAN + "/status",       UNAUTHORIZED, FORBIDDEN, OK),
                route(HttpMethod.POST,  "/api/v1/transfers",                          UNAUTHORIZED, OK,        FORBIDDEN),
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
        MockHttpServletRequestBuilder builder = request(method, path).header("Idempotency-Key", "test-key");
        if (method == HttpMethod.POST && path.startsWith("/api/v1/auth/")) {
            String body;
            if (path.endsWith("/register")) {
                body = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john@example.com\","
                        + "\"phoneNumber\":\"+393401234567\",\"taxCode\":\"DOEJHN90A01H501X\",\"password\":\"password123\"}";
            } else if (path.endsWith("/login")) {
                body = "{\"email\":\"john@example.com\",\"password\":\"password123\"}";
            } else {
                body = "{\"refreshToken\":\"test-refresh-token\"}";
            }
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return builder;
    }
}
