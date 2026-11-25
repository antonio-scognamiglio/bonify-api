package com.bonifybank.bonify.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;

class AuthServiceTests {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    // Low cost factor: real hashing, fast tests
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(customerRepository, passwordEncoder, jwtService, refreshTokenService);
        when(customerRepository.save(any())).thenAnswer(invocation -> {
            CustomerEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void registerCreatesCustomerWithHashedPasswordAndNormalizedFields() {
        CustomerEntity saved = authService.register(" Anna ", "Rossi", "Anna@Example.COM", " +393401234567 ",
                " rssnna90a41h501n ", "password123");

        verify(customerRepository).save(saved);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFirstName()).isEqualTo("Anna");
        assertThat(saved.getEmail()).isEqualTo("anna@example.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("+393401234567");
        assertThat(saved.getTaxCode()).isEqualTo("RSSNNA90A41H501N");
        assertThat(saved.getRole()).isEqualTo(CustomerRole.CUSTOMER);
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    void registerRejectsEmailAlreadyUsedIgnoringCase() {
        when(customerRepository.existsByEmail("anna@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("Anna", "Rossi", "ANNA@example.com", "+393401234567",
                "RSSNNA90A41H501N", "password123"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCodes.EMAIL_ALREADY_USED));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void registerRejectsTaxCodeAlreadyUsedIgnoringCase() {
        when(customerRepository.existsByTaxCode("RSSNNA90A41H501N")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("Anna", "Rossi", "anna@example.com", "+393401234567",
                "rssnna90a41h501n", "password123"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCodes.TAX_CODE_ALREADY_USED));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void loginIssuesTokenForValidCredentials() {
        CustomerEntity customer = existingCustomer("password123");
        when(customerRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(customer));
        when(jwtService.generateToken(customer)).thenReturn("jwt");
        when(refreshTokenService.issue(customer)).thenReturn("refresh");

        assertThat(authService.login(" Anna@Example.com ", "password123"))
                .isEqualTo(new AuthTokens("jwt", "refresh"));
    }

    @Test
    void refreshIssuesAccessTokenForTheRotatedSession() {
        CustomerEntity customer = existingCustomer("password123");
        when(refreshTokenService.rotate("old-refresh"))
                .thenReturn(new RefreshTokenService.Rotation(customer, "new-refresh"));
        when(jwtService.generateToken(customer)).thenReturn("jwt");

        assertThat(authService.refresh("old-refresh")).isEqualTo(new AuthTokens("jwt", "new-refresh"));
    }

    @Test
    void loginWithWrongPasswordAndUnknownEmailFailTheSameWay() {
        when(customerRepository.findByEmail("anna@example.com"))
                .thenReturn(Optional.of(existingCustomer("password123")));
        when(customerRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        BusinessException wrongPassword = loginFailure("anna@example.com", "wrong-pass");
        BusinessException unknownEmail = loginFailure("nobody@example.com", "password123");

        assertThat(wrongPassword.getCode()).isEqualTo(ErrorCodes.UNAUTHORIZED);
        assertThat(unknownEmail.getCode()).isEqualTo(ErrorCodes.UNAUTHORIZED);
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
        verify(jwtService, never()).generateToken(any());
        verify(refreshTokenService, never()).issue(any());
    }

    private BusinessException loginFailure(String email, String password) {
        try {
            authService.login(email, password);
        } catch (BusinessException e) {
            return e;
        }
        throw new AssertionError("login should have failed");
    }

    private CustomerEntity existingCustomer(String rawPassword) {
        return CustomerEntity.builder()
                .id(UUID.randomUUID())
                .email("anna@example.com")
                .password(passwordEncoder.encode(rawPassword))
                .role(CustomerRole.CUSTOMER)
                .build();
    }
}
