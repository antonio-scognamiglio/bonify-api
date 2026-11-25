package com.bonifybank.bonify.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.JwtProperties;
import com.bonifybank.bonify.config.SecurityConfig;
import com.bonifybank.bonify.config.WebConfig;
import com.bonifybank.bonify.modules.customer.CustomerController;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.customer.CustomerService;

/**
 * FR-AUTH-02 / FR-AUTH-03 with real tokens: issued by JwtService, validated by the security filter chain.
 * 200 = the request passed security and reached the controller (the service is mocked).
 */
@WebMvcTest(CustomerController.class)
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class, JwtService.class })
@TestPropertySource(properties = "bonify.jwt.secret=" + JwtAuthenticationTests.SECRET)
class JwtAuthenticationTests {

    static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    private static final String ME = "/api/v1/customers/me";
    private static final String ALL_CUSTOMERS = "/api/v1/customers";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerService customerService;

    @BeforeEach
    void stubService() {
        when(customerService.listCustomers(anyInt(), anyInt())).thenReturn(Page.empty());
    }

    @Autowired
    JwtService jwtService;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtProperties properties;

    @Test
    void tokenContainsCustomerIdAsSubjectAndRole() {
        CustomerEntity customer = customer(CustomerRole.CUSTOMER);

        String token = jwtService.generateToken(customer);
        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwtService.getExpirationSeconds()).isEqualTo(300);
        assertThat(jwt.getSubject()).isEqualTo(customer.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("bonify");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
        // No personal data in the token
        assertThat(jwt.getClaims()).doesNotContainKeys("email", "password");
    }

    @Test
    void customerTokenReachesCustomerEndpoint() throws Exception {
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(CustomerRole.CUSTOMER)))
                .andExpect(status().isOk());
    }

    @Test
    void customerTokenIsForbiddenOnAdminEndpoint() throws Exception {
        mockMvc.perform(get(ALL_CUSTOMERS).header(HttpHeaders.AUTHORIZATION, bearer(CustomerRole.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenReachesAdminEndpoint() throws Exception {
        mockMvc.perform(get(ALL_CUSTOMERS).header(HttpHeaders.AUTHORIZATION, bearer(CustomerRole.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        // Issued 2 hours ago with a 5-minute validity (beyond the default 60s clock skew)
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = new JwtService(jwtEncoder, properties, twoHoursAgo)
                .generateToken(customer(CustomerRole.CUSTOMER));

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretIsUnauthorized() throws Exception {
        JwtProperties otherSecret = new JwtProperties("another-secret-that-is-at-least-32-bytes",
                properties.issuer(), properties.expiration(), properties.refreshExpiration());
        JwtEncoder otherEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                otherSecret.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        String token = new JwtService(otherEncoder, otherSecret)
                .generateToken(customer(CustomerRole.ADMIN));

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsUnauthorized() throws Exception {
        JwtProperties otherIssuer = new JwtProperties(SECRET, "someone-else", properties.expiration(),
                properties.refreshExpiration());
        String token = new JwtService(jwtEncoder, otherIssuer)
                .generateToken(customer(CustomerRole.CUSTOMER));

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(CustomerRole role) {
        return "Bearer " + jwtService.generateToken(customer(role));
    }

    private static CustomerEntity customer(CustomerRole role) {
        return CustomerEntity.builder()
                .id(UUID.randomUUID())
                .email("anna@example.com")
                .role(role)
                .build();
    }
}
