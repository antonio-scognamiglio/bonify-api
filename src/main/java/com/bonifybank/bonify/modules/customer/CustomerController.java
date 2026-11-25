package com.bonifybank.bonify.modules.customer;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bonifybank.bonify.common.dto.PagedResponse;
import com.bonifybank.bonify.modules.customer.dto.ChangePasswordRequest;
import com.bonifybank.bonify.modules.customer.dto.CustomerResponse;
import com.bonifybank.bonify.modules.customer.dto.UpdateProfileRequest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Every customer edits only their own data (BR-12). The admin can list customers but cannot
 * change other customers' data or roles: the only admin account is created directly in the database.
 */
@RestController
@RequestMapping("/customers")
// No class-level @Validated: Spring MVC validates the @Min/@Max parameters itself and raises
// HandlerMethodValidationException (400); @Validated would switch to AOP validation instead
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // FR-CUS-01: own profile
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @GetMapping(path = "/me", version = "1+")
    public ResponseEntity<CustomerResponse> getMe() {
        return ResponseEntity.ok(customerService.getMe());
    }

    // FR-CUS-02: partial update of the own personal data
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PatchMapping(path = "/me", version = "1+")
    public ResponseEntity<CustomerResponse> updateMe(@Validated @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(customerService.updateMe(request));
    }

    // FR-CUS-03: returns 204, every session of the customer is revoked by the service
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PutMapping(path = "/me/password", version = "1+")
    public ResponseEntity<Void> changeMyPassword(@Validated @RequestBody ChangePasswordRequest request) {
        customerService.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }

    // FR-CUS-04: paged list, newest first
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(version = "1+")
    public ResponseEntity<PagedResponse<CustomerResponse>> listCustomers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(PagedResponse.from(customerService.listCustomers(page, size)));
    }
}
