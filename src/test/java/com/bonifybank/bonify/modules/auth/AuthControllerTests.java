package com.bonifybank.bonify.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.JwtProperties;
import com.bonifybank.bonify.config.SecurityConfig;
import com.bonifybank.bonify.config.WebConfig;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;

/**
 * FR-AUTH-01 / FR-AUTH-02 at the controller level, with a real AuthService + JwtService
 * and a mocked repository (no database).
 */
@WebMvcTest(AuthController.class)
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class, JwtService.class, AuthService.class,
        RefreshTokenService.class })
@TestPropertySource(properties = "bonify.jwt.secret=" + AuthControllerTests.SECRET)
class AuthControllerTests {

    static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtProperties jwtProperties;

    @MockitoBean
    CustomerRepository customerRepository;

    @MockitoBean
    RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        when(customerRepository.save(any()))
                .thenAnswer(invocation -> {
                    CustomerEntity saved = invocation.getArgument(0);
                    if (saved != null && saved.getId() == null) {
                        saved.setId(UUID.randomUUID());
                    }
                    return saved;
                });
    }

    @Test
    void registerReturnsProfileWithoutPassword() throws Exception {
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);

        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody("Anna@Example.COM", "+39 340 1234567", "rssnna90a41h501n",
                                        "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+39 340 1234567"))
                .andExpect(jsonPath("$.taxCode").value("RSSNNA90A41H501N"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerRejectsInvalidInput() throws Exception {
        // password too short (min 8)
        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody("anna@example.com", "+393401234567", "RSSNNA90A41H501N",
                                        "1234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerRequiresPhoneAndTaxCode() throws Exception {
        String body = """
                {"firstName":"Anna","lastName":"Rossi","email":"anna@example.com","password":"password123"}
                """;
        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerRejectsInvalidTaxCodeAndPhoneFormat() throws Exception {
        // "Z" is not a valid month letter
        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("anna@example.com", "+393401234567", "RSSNNA90Z41H501N", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("anna@example.com", "call-me", "RSSNNA90A41H501N", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerRejectsEmailAlreadyUsed() throws Exception {
        when(customerRepository.existsByEmail("anna@example.com")).thenReturn(true);

        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody("anna@example.com", "+393401234567", "RSSNNA90A41H501N",
                                        "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void registerRejectsTaxCodeAlreadyUsed() throws Exception {
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByTaxCode("RSSNNA90A41H501N")).thenReturn(true);

        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody("anna@example.com", "+393401234567", "rssnna90a41h501n",
                                        "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAX_CODE_ALREADY_USED"));
    }

    @Test
    void loginWithShortWrongPasswordIsUnauthorizedNotBadRequest() throws Exception {
        when(customerRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(existingCustomer("password123")));

        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"password\":\"password123\"}", "{\"email\":\"\",\"password\":\"password123\"}",
            "{\"email\":\"   \",\"password\":\"password123\"}", "{\"email\":\"anna@example.com\"}",
            "{\"email\":\"anna@example.com\",\"password\":\"\"}",
            "{\"email\":\"anna@example.com\",\"password\":\"   \"}" })
    void loginWithMissingOrBlankFieldsIsBadRequest(String body) throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void emailAlreadyUsedProblemTypeUsesDashes() throws Exception {
        when(customerRepository.existsByEmail("anna@example.com")).thenReturn(true);

        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("anna@example.com", "+393401234567", "RSSNNA90A41H501N", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://bonify.example/errors/email-already-used"));
    }

    private static byte[] registerBody(String email, String phoneNumber, String taxCode, String password) {
        return ("{\"firstName\":\"Anna\",\"lastName\":\"Rossi\",\"email\":\"" + email
                + "\",\"phoneNumber\":\"" + phoneNumber + "\",\"taxCode\":\"" + taxCode
                + "\",\"password\":\"" + password + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void loginReturnsAccessToken() throws Exception {
        CustomerEntity customer = existingCustomer("password123");
        when(customerRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(customer));

        String body = """
                {"email":"anna@example.com","password":"password123"}
                """;
        mockMvc.perform(
                        post(LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800));

        // A new session replaces the previous ones; the token is stored as a hash, never in clear
        verify(refreshTokenRepository).revokeAllByCustomer(eq(customer.getId()), any());
        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).hasSize(64);
        assertThat(saved.getValue().getCustomer()).isSameAs(customer);
    }

    @Test
    void refreshReturnsNewTokenPairEvenWithExpiredAccessTokenInHeader() throws Exception {
        CustomerEntity customer = existingCustomer("password123");
        RefreshTokenEntity stored = storedToken(customer, "old-refresh");
        when(refreshTokenRepository.markUsed(eq(stored.getId()), any())).thenReturn(1);

        // Clients often attach the (expired) access token to every call: it must be ignored here
        String expiredAccessToken = new JwtService(jwtEncoder, jwtProperties,
                Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC)).generateToken(customer);

        mockMvc.perform(post(REFRESH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(not("old-refresh")));
    }

    @Test
    void refreshWithUnknownTokenIsUnauthorized() throws Exception {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("unknown")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired refresh token"));
    }

    @Test
    void reusingARotatedTokenIsUnauthorizedAndRevokesTheSession() throws Exception {
        RefreshTokenEntity stored = storedToken(existingCustomer("password123"), "already-used");
        stored.setUsedAt(Instant.now().minusSeconds(60));

        mockMvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("already-used")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired refresh token"));

        verify(refreshTokenRepository).revokeFamily(eq(stored.getFamilyId()), any());
    }

    @Test
    void refreshWithoutTokenIsBadRequest() throws Exception {
        mockMvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void logoutRevokesTheSessionAndReturnsNoContent() throws Exception {
        RefreshTokenEntity stored = storedToken(existingCustomer("password123"), "current-refresh");

        mockMvc.perform(post(LOGOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("current-refresh")))
                .andExpect(status().isNoContent());

        verify(refreshTokenRepository).revokeFamily(eq(stored.getFamilyId()), any());
    }

    @Test
    void logoutWithUnknownTokenStillReturnsNoContent() throws Exception {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post(LOGOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("unknown")))
                .andExpect(status().isNoContent());
    }

    private RefreshTokenEntity storedToken(CustomerEntity customer, String rawToken) {
        RefreshTokenEntity stored = RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .tokenHash(RefreshTokenService.hash(rawToken))
                .familyId(UUID.randomUUID())
                .expiresAt(Instant.now().plus(Duration.ofDays(1)))
                .build();
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(rawToken)))
                .thenReturn(Optional.of(stored));
        return stored;
    }

    private static byte[] refreshBody(String refreshToken) {
        return ("{\"refreshToken\":\"" + refreshToken + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailIsUnauthorized() throws Exception {
        CustomerEntity customer = existingCustomer("password123");
        when(customerRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(customer));
        when(customerRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        // Both failures must look identical (same 401 detail), so the email is not revealed
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"anna@example.com","password":"wrong-password"}
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"password123"}
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    private static CustomerEntity existingCustomer(String rawPassword) {
        return CustomerEntity.builder()
                .id(UUID.randomUUID())
                .email("anna@example.com")
                .password(new BCryptPasswordEncoder(4).encode(rawPassword))
                .role(CustomerRole.CUSTOMER)
                .build();
    }
}
