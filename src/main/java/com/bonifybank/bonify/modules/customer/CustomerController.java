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
import com.bonifybank.bonify.config.ApiExamples;
import com.bonifybank.bonify.config.ApiSchemas;
import com.bonifybank.bonify.config.OpenApiConfig;
import com.bonifybank.bonify.exception.dto.ErrorResponse;
import com.bonifybank.bonify.modules.customer.dto.ChangePasswordRequest;
import com.bonifybank.bonify.modules.customer.dto.CustomerResponse;
import com.bonifybank.bonify.modules.customer.dto.UpdateProfileRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiConfig.TAG_CUSTOMERS)
public class CustomerController {

    private final CustomerService customerService;

    // FR-CUS-01: own profile
    @Operation(summary = "Get my profile", description = "Profile of the logged-in customer (or admin).")
    @ApiResponse(responseCode = "200", description = "Own profile",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = CustomerResponse.class),
                    examples = @ExampleObject(value = ApiExamples.CUSTOMER)))
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @GetMapping(path = "/me", version = "1+")
    public ResponseEntity<CustomerResponse> getMe() {
        return ResponseEntity.ok(customerService.getMe());
    }

    // FR-CUS-02: partial update of the own personal data
    @Operation(summary = "Update my profile", description = """
            Partial update: send only the fields to change, the others stay as they are.
            Role, password and tax code cannot be changed here (unknown fields are ignored).
            The new email must not belong to another customer.
            """)
    @ApiResponse(responseCode = "200", description = "Updated profile",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = CustomerResponse.class),
                    examples = @ExampleObject(value = ApiExamples.CUSTOMER)))
    @ApiResponse(responseCode = "400", description = "Invalid fields",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already used by another customer",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.EMAIL_ALREADY_USED)))
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PatchMapping(path = "/me", version = "1+")
    public ResponseEntity<CustomerResponse> updateMe(@Validated @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(customerService.updateMe(request));
    }

    // FR-CUS-03: returns 204, every session of the customer is revoked by the service
    @Operation(summary = "Change my password", description = """
            Requires the current password. On success every session is closed: the refresh tokens stop
            working, so the client must log in again with the new password once the access token expires.
            """)
    @ApiResponse(responseCode = "204", description = "Password changed, sessions revoked")
    @ApiResponse(responseCode = "400", description = "Wrong current password, or new password invalid / same as the current one",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "WRONG_CURRENT_PASSWORD", value = ApiExamples.WRONG_CURRENT_PASSWORD),
                            @ExampleObject(name = "Same password", value = ApiExamples.VALIDATION_ERROR_SAME_PASSWORD)
                    }))
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PutMapping(path = "/me/password", version = "1+")
    public ResponseEntity<Void> changeMyPassword(@Validated @RequestBody ChangePasswordRequest request) {
        customerService.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }

    // FR-CUS-04: paged list, newest first
    @Operation(summary = "List customers (admin)", description = "Paged list of all customers, newest first.")
    @ApiResponse(responseCode = "200", description = "One page of customers",
            content = @Content(mediaType = ApiExamples.JSON, schema = @Schema(implementation = ApiSchemas.CustomerPage.class),
                    examples = @ExampleObject(value = ApiExamples.CUSTOMER_PAGE)))
    @ApiResponse(responseCode = "400", description = "Invalid page or size",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.VALIDATION_ERROR_PAGING)))
    @ApiResponse(responseCode = "403", description = "Caller is not an admin",
            content = @Content(mediaType = ApiExamples.PROBLEM_JSON,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.FORBIDDEN_ROLE)))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(version = "1+")
    public ResponseEntity<PagedResponse<CustomerResponse>> listCustomers(
            @Parameter(description = "Page number, from 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, from 1 to 100", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(PagedResponse.from(customerService.listCustomers(page, size)));
    }
}
