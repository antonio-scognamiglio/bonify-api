package com.bonifybank.bonify.modules.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import com.bonifybank.bonify.config.JwtConfig;
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
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class, JwtService.class, AuthService.class })
@TestPropertySource(properties = "bonify.jwt.secret=" + AuthControllerTests.SECRET)
class AuthControllerTests {

    static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerRepository customerRepository;

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

        String body = """
                {"firstName":"Anna","lastName":"Rossi","email":"Anna@Example.COM","password":"password123"}
                """;
        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerRejectsInvalidInput() throws Exception {
        // password too short (min 8)
        String body = """
                {"firstName":"Anna","lastName":"Rossi","email":"anna@example.com","password":"1234567"}
                """;
        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerRejectsEmailAlreadyUsed() throws Exception {
        when(customerRepository.existsByEmail("anna@example.com")).thenReturn(true);

        String body = """
                {"firstName":"Anna","lastName":"Rossi","email":"anna@example.com","password":"password123"}
                """;
        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
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
                .andExpect(jsonPath("$.expiresIn").value(3600));
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
