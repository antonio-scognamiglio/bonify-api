package com.bonifybank.bonify.modules.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;

import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;

import lombok.RequiredArgsConstructor;

/**
 * Registration and login logic. Takes plain values so it does not depend on the request DTOs:
 * input validation (@Valid on the DTOs) happens in the controller before these methods are called.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    // Same message for unknown email and wrong password, so that existing emails are not revealed (FR-AUTH-02)
    static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * FR-AUTH-01: public registration always creates a CUSTOMER (admins come from seed data only).
     *
     * @return the saved customer; map it to the profile DTO (never expose the password hash)
     * @throws BusinessException EMAIL_ALREADY_USED if the email is already registered
     */
    @Transactional
    public CustomerEntity register(String firstName, String lastName, String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (customerRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCodes.EMAIL_ALREADY_USED, "Email already registered");
        }
        CustomerEntity customer = CustomerEntity.builder()
                .firstName(firstName.strip())
                .lastName(lastName.strip())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(rawPassword))
                .role(CustomerRole.CUSTOMER)
                .build();
        return customerRepository.save(customer);
    }

    /**
     * FR-AUTH-02: checks the credentials and issues a JWT.
     *
     * @return the access token; expiresIn is {@link JwtService#getExpirationSeconds()}
     * @throws BusinessException UNAUTHORIZED for unknown email or wrong password (same message)
     */
    @Transactional(readOnly = true)
    public String login(String email, String rawPassword) {
        CustomerEntity customer = customerRepository.findByEmail(normalizeEmail(email))
                .filter(found -> passwordEncoder.matches(rawPassword, found.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, INVALID_CREDENTIALS));
        return jwtService.generateToken(customer);
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
