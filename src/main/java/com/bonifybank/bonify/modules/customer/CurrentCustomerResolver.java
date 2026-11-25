package com.bonifybank.bonify.modules.customer;

import java.util.Locale;

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

        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Authenticated customer not found"));
    }
}
