package com.bonifybank.bonify.modules.customer;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;

    public CustomerService (CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public CustomerResponse getMe(){
        CustomerEntity current = getCurrentCustomer();
        return toResponse(current);
    }

    @Transactional
    public CustomerResponse updateMe(UpdateProfileRequest request){
        CustomerEntity current = getCurrentCustomer();

        if(request.firstName() != null && !request.firstName().isEmpty()){
            current.setFirstName(request.firstName().trim());
        }

        if (request.lastName() != null && !request.lastName().isEmpty()){
            current.setLastName(request.lastName().trim());
        }

        if (request.phoneNumber() != null && !request.phoneNumber().isEmpty()){
            current.setPhoneNumber(request.phoneNumber().trim());
        }

        CustomerEntity saved = customerRepository.save(current);
        return toResponse(saved);
    }

    @Transactional
    public void changeMyPassword(ChangePasswordRequest request) {
        CustomerEntity current = getCurrentCustomer();

        //TO DO REPLACE EXCEPTION
        if (!matchesHash(request.currentPassword(), current.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        //TO DO REPLACE EXCEPTION
        if (request.currentPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        current.setPassword(hashPassword(request.newPassword()));
        customerRepository.save(current);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> listCustomers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return customerRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, AdminUpdateCustomerRequest request) {
        //TO DO REPLACE EXCEPTION
        CustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));

        if (request.role() != null && !request.role().isBlank()) {
            CustomerRole newRole = CustomerRole.valueOf(request.role().trim().toUpperCase());
            customer.setRole(newRole);
        }

        CustomerEntity saved = customerRepository.save(customer);
        return toResponse(saved);
    }


    private CustomerEntity getCurrentCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated user found");
        }

        String email = authentication.getName().trim().toLowerCase();
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated customer not found"));
    }

    private boolean matchesHash(String rawPassword, String storedHash) {
        return hashPassword(rawPassword).equals(storedHash);
    }

    //TO DO FIX IMPLEMENT THIS METHOD
    private String hashPassword(String rawPassword) {
        throw new UnsupportedOperationException("Password hashing method not configured yet");
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
                customer.getUpdatedAt()
        );
    }
}
