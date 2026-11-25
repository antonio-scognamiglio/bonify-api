package com.bonifybank.bonify.modules.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.auth.RefreshTokenService;
import com.bonifybank.bonify.modules.customer.dto.ChangePasswordRequest;
import com.bonifybank.bonify.modules.customer.dto.CustomerResponse;
import com.bonifybank.bonify.modules.customer.dto.UpdateProfileRequest;

/** FR-CUS-02 / BR-10: email update, normalized and unique across customers. */
class CustomerServiceTests {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CurrentCustomerResolver currentCustomerResolver = mock(CurrentCustomerResolver.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private CustomerService customerService;
    private CustomerEntity current;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, currentCustomerResolver,
                passwordEncoder, refreshTokenService);
        current = CustomerEntity.builder()
                .id(UUID.randomUUID())
                .firstName("Anna")
                .lastName("Rossi")
                .email("anna@example.com")
                .phoneNumber("+393401234567")
                .taxCode("RSSNNA90A41H501X")
                .password(passwordEncoder.encode("OldPassw0rd"))
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
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

    // FR-CUS-01
    @Test
    void getMeMapsTheCurrentCustomer() {
        CustomerResponse response = customerService.getMe();

        assertThat(response.id()).isEqualTo(current.getId());
        assertThat(response.email()).isEqualTo("anna@example.com");
        assertThat(response.firstName()).isEqualTo("Anna");
        assertThat(response.lastName()).isEqualTo("Rossi");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    // FR-CUS-02
    @Test
    void updateMeWithOnlyOneFieldLeavesTheOthersUnchanged() {
        CustomerResponse response = customerService.updateMe(new UpdateProfileRequest(null, "Bianchi", null, null));

        assertThat(response.lastName()).isEqualTo("Bianchi");
        assertThat(response.firstName()).isEqualTo("Anna");
        assertThat(response.email()).isEqualTo("anna@example.com");
        assertThat(response.phoneNumber()).isEqualTo("+393401234567");
    }

    @Test
    void updateMeTrimsValuesAndNeverTouchesRoleOrPassword() {
        String passwordBefore = current.getPassword();

        CustomerResponse response = customerService.updateMe(
                new UpdateProfileRequest("  Maria ", " Verdi  ", null, " +39 340 000 0000 "));

        assertThat(response.firstName()).isEqualTo("Maria");
        assertThat(response.lastName()).isEqualTo("Verdi");
        assertThat(response.phoneNumber()).isEqualTo("+39 340 000 0000");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        assertThat(current.getPassword()).isEqualTo(passwordBefore);
    }

    // FR-CUS-03 / BR-11
    @Test
    void changePasswordEncodesSavesAndRevokesRefreshTokens() {
        customerService.changeMyPassword(new ChangePasswordRequest("OldPassw0rd", "NewPassw0rd"));

        ArgumentCaptor<CustomerEntity> captor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).save(captor.capture());
        String stored = captor.getValue().getPassword();
        assertThat(stored).isNotEqualTo("NewPassw0rd");
        assertThat(passwordEncoder.matches("NewPassw0rd", stored)).isTrue();
        verify(refreshTokenService).revokeAll(current.getId());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordChangesNothing() {
        String passwordBefore = current.getPassword();

        assertThatThrownBy(() -> customerService.changeMyPassword(new ChangePasswordRequest("WrongPassw0rd", "NewPassw0rd")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.WRONG_CURRENT_PASSWORD);

        verify(customerRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
        assertThat(current.getPassword()).isEqualTo(passwordBefore);
    }

    @Test
    void changePasswordToTheSamePasswordIsValidationError() {
        String passwordBefore = current.getPassword();

        assertThatThrownBy(() -> customerService.changeMyPassword(new ChangePasswordRequest("OldPassw0rd", "OldPassw0rd")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);

        verify(customerRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
        assertThat(current.getPassword()).isEqualTo(passwordBefore);
    }

    // FR-CUS-04
    @Test
    void listCustomersUsesGivenPageAndSizeSortedByCreatedAtDescending() {
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(current)));

        Page<CustomerResponse> result = customerService.listCustomers(2, 15);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findAll(captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(15);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(result.getContent()).extracting(CustomerResponse::id).containsExactly(current.getId());
    }
}
