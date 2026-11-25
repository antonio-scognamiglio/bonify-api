package com.bonifybank.bonify.modules.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.auth.RefreshTokenService;
import com.bonifybank.bonify.modules.customer.dto.CustomerResponse;
import com.bonifybank.bonify.modules.customer.dto.UpdateProfileRequest;

/** FR-CUS-02 / BR-10: email update, normalized and unique across customers. */
class CustomerServiceTests {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CurrentCustomerResolver currentCustomerResolver = mock(CurrentCustomerResolver.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    private CustomerService customerService;
    private CustomerEntity current;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, currentCustomerResolver,
                new BCryptPasswordEncoder(4), refreshTokenService);
        current = CustomerEntity.builder()
                .id(UUID.randomUUID())
                .firstName("Anna")
                .lastName("Rossi")
                .email("anna@example.com")
                .phoneNumber("+393401234567")
                .role(CustomerRole.CUSTOMER)
                .build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(current);
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void emailIsNormalizedAndUpdated() {
        CustomerResponse response = customerService.updateMe(
                new UpdateProfileRequest(null, null, "  Anna.New@Example.COM ", null));

        assertThat(response.email()).isEqualTo("anna.new@example.com");
        // Fields not sent stay unchanged
        assertThat(response.firstName()).isEqualTo("Anna");
        assertThat(response.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void emailOfAnotherCustomerIsConflict() {
        when(customerRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.updateMe(new UpdateProfileRequest(null, null, "taken@example.com", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.EMAIL_ALREADY_USED);

        verify(customerRepository, never()).save(any());
    }

    @Test
    void ownEmailInAnotherCaseIsNotConflict() {
        CustomerResponse response = customerService.updateMe(new UpdateProfileRequest(null, null, "ANNA@example.com", null));

        assertThat(response.email()).isEqualTo("anna@example.com");
        verify(customerRepository, never()).existsByEmail(anyString());
    }
}
