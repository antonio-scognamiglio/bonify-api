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
 * Registration, login, refresh and logout logic. Takes plain values so it does not depend on the request DTOs:
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
    private final RefreshTokenService refreshTokenService;

    /**
     * FR-AUTH-01: public registration always creates a CUSTOMER (admins come from seed data only).
     *
     * @return the saved customer; map it to the profile DTO (never expose the password hash)
     * @throws BusinessException EMAIL_ALREADY_USED if the email is already registered,
     *                           TAX_CODE_ALREADY_USED if the tax code is already registered
     */
    @Transactional
    public CustomerEntity register(String firstName, String lastName, String email, String phoneNumber,
            String taxCode, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedTaxCode = taxCode.strip().toUpperCase(Locale.ROOT);
        if (customerRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCodes.EMAIL_ALREADY_USED, "Email already registered");
        }
        if (customerRepository.existsByTaxCode(normalizedTaxCode)) {
            throw new BusinessException(ErrorCodes.TAX_CODE_ALREADY_USED, "Tax code already registered");
        }
        CustomerEntity customer = CustomerEntity.builder()
                .firstName(firstName.strip())
                .lastName(lastName.strip())
                .email(normalizedEmail)
                .phoneNumber(phoneNumber.strip())
                .taxCode(normalizedTaxCode)
                .password(passwordEncoder.encode(rawPassword))
                .role(CustomerRole.CUSTOMER)
                .build();
        return customerRepository.save(customer);
    }

    /**
     * FR-AUTH-02: checks the credentials and issues an access token plus a refresh token.
     * The new refresh token replaces the previous one: only one session per customer.
     *
     * @return the token pair; validities from {@link JwtService} and {@link RefreshTokenService}
     * @throws BusinessException UNAUTHORIZED for unknown email or wrong password (same message)
     */
    @Transactional
    public AuthTokens login(String email, String rawPassword) {
        CustomerEntity customer = customerRepository.findByEmail(normalizeEmail(email))
                .filter(found -> passwordEncoder.matches(rawPassword, found.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, INVALID_CREDENTIALS));
        String refreshToken = refreshTokenService.issue(customer);
        return new AuthTokens(jwtService.generateToken(customer), refreshToken);
    }

    /**
     * FR-AUTH-04: swaps a valid refresh token for a new pair (rotation: the old one stops working).
     * No rollback on BusinessException: on a detected reuse (FR-AUTH-06) the session revocation
     * must be committed even though the request fails with 401.
     *
     * @throws BusinessException UNAUTHORIZED if the token is unknown, revoked, expired or already used
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthTokens refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return new AuthTokens(jwtService.generateToken(rotation.customer()), rotation.refreshToken());
    }

    /**
     * FR-AUTH-05: ends the session. Already issued access tokens stay valid until they expire
     * (stateless API, NFR-02), so the client must also discard its access token.
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
