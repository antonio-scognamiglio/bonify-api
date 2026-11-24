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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;

class AuthServiceTests {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    // Low cost factor: real hashing, fast tests
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(customerRepository, passwordEncoder, jwtService);
        when(customerRepository.save(any())).thenAnswer(invocation -> {
            CustomerEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void registerCreatesCustomerWithHashedPasswordAndLowerCaseEmail() {
        CustomerEntity saved = authService.register(" Anna ", "Rossi", "Anna@Example.COM", "password123");

        verify(customerRepository).save(saved);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFirstName()).isEqualTo("Anna");
        assertThat(saved.getEmail()).isEqualTo("anna@example.com");
        assertThat(saved.getRole()).isEqualTo(CustomerRole.CUSTOMER);
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    void registerRejectsEmailAlreadyUsedIgnoringCase() {
        when(customerRepository.existsByEmail("anna@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("Anna", "Rossi", "ANNA@example.com", "password123"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void loginIssuesTokenForValidCredentials() {
        CustomerEntity customer = existingCustomer("password123");
        when(customerRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(customer));
        when(jwtService.generateToken(customer)).thenReturn("jwt");

        assertThat(authService.login(" Anna@Example.com ", "password123")).isEqualTo("jwt");
    }

    @Test
    void loginWithWrongPasswordAndUnknownEmailFailTheSameWay() {
        when(customerRepository.findByEmail("anna@example.com"))
                .thenReturn(Optional.of(existingCustomer("password123")));
        when(customerRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        ResponseStatusException wrongPassword = loginFailure("anna@example.com", "wrong-pass");
        ResponseStatusException unknownEmail = loginFailure("nobody@example.com", "password123");

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getReason()).isEqualTo(unknownEmail.getReason());
        verify(jwtService, never()).generateToken(any());
    }

    private ResponseStatusException loginFailure(String email, String password) {
        try {
            authService.login(email, password);
        } catch (ResponseStatusException e) {
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
