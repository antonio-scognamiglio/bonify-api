package com.bonifybank.bonify.modules.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;

/** The authenticated name is the JWT subject, i.e. the customer id (FR-AUTH-02), not the email. */
class CurrentCustomerResolverTests {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CurrentCustomerResolver resolver = new CurrentCustomerResolver(customerRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesCustomerBySubjectId() {
        CustomerEntity customer = CustomerEntity.builder().id(UUID.randomUUID()).email("anna@example.com").build();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        authenticateAs(customer.getId().toString());

        assertThat(resolver.currentCustomer()).isSameAs(customer);
    }

    @Test
    void unknownIdIsNotFound() {
        authenticateAs(UUID.randomUUID().toString());

        assertThatThrownBy(resolver::currentCustomer)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.NOT_FOUND);
    }

    @Test
    void nonUuidSubjectIsUnauthorized() {
        authenticateAs("anna@example.com");

        assertThatThrownBy(resolver::currentCustomer)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.UNAUTHORIZED);
    }

    @Test
    void missingAuthenticationIsUnauthorized() {
        assertThatThrownBy(resolver::currentCustomer)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.UNAUTHORIZED);
    }

    private static void authenticateAs(String name) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(name, null, List.of()));
    }
}
