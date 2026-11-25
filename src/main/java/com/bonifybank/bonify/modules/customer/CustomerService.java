package com.bonifybank.bonify.modules.customer;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.customer.dto.AdminUpdateCustomerRequest;
import com.bonifybank.bonify.modules.customer.dto.ChangePasswordRequest;
import com.bonifybank.bonify.modules.customer.dto.CustomerResponse;
import com.bonifybank.bonify.modules.customer.dto.UpdateProfileRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final CurrentCustomerResolver currentCustomerResolver;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(CustomerRepository customerRepository, CurrentCustomerResolver currentCustomerResolver,
            PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.currentCustomerResolver = currentCustomerResolver;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public CustomerResponse getMe() {
        CustomerEntity current = currentCustomerResolver.currentCustomer();
        return toResponse(current);
    }

    @Transactional
    public CustomerResponse updateMe(UpdateProfileRequest request) {
        CustomerEntity current = currentCustomerResolver.currentCustomer();

        if (request.firstName() != null && !request.firstName().isEmpty()) {
            current.setFirstName(request.firstName().trim());
        }

        if (request.lastName() != null && !request.lastName().isEmpty()) {
            current.setLastName(request.lastName().trim());
        }

        if (request.phoneNumber() != null && !request.phoneNumber().isEmpty()) {
            current.setPhoneNumber(request.phoneNumber().trim());
        }

        CustomerEntity saved = customerRepository.save(current);
        return toResponse(saved);
    }

    @Transactional
    public void changeMyPassword(ChangePasswordRequest request) {
        CustomerEntity current = currentCustomerResolver.currentCustomer();

        if (!passwordEncoder.matches(request.currentPassword(), current.getPassword())) {
            throw new BusinessException(ErrorCodes.WRONG_CURRENT_PASSWORD, "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR,
                    "New password must be different from current password");
        }

        current.setPassword(passwordEncoder.encode(request.newPassword()));
        customerRepository.save(current);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> listCustomers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return customerRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, AdminUpdateCustomerRequest request) {
        CustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Customer not found: " + id));

        if (request.role() != null && !request.role().isBlank()) {
            CustomerRole newRole = CustomerRole.valueOf(request.role().trim().toUpperCase());
            customer.setRole(newRole);
        }

        CustomerEntity saved = customerRepository.save(customer);
        return toResponse(saved);
    }

    private CustomerResponse toResponse(CustomerEntity customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPhoneNumber(),
                customer.getTaxCode(),
                customer.getRole().name(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
