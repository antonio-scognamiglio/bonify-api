package com.bonifybank.bonify.modules.customer;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;

@Component
public class CurrentCustomerResolver {

    private final CustomerRepository customerRepository;

    public CurrentCustomerResolver(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerEntity currentCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "No authenticated user found");
        }

        // The JWT subject is the customer id, not the email (FR-AUTH-02): it survives an email change
        UUID customerId;
        try {
            customerId = UUID.fromString(authentication.getName().trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "Invalid token subject");
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Authenticated customer not found"));
    }
}
