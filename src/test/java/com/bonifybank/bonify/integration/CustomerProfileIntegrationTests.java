package com.bonifybank.bonify.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Feature "Customer profile" of the acceptance criteria (functional-analysis.md section 10) and the
 * admin customer list: FR-CUS-01 ... 05, BR-10 / 11 / 12.
 */
class CustomerProfileIntegrationTests extends IntegrationTestSupport {

    @Test
    void updatePersonalData() throws Exception {
        Customer customer = newCustomer();
        String newEmail = newEmail();

        patchAs(customer.token(), "/customers/me", """
                {"lastName":"Verdi","email":"%s"}""".formatted(newEmail.toUpperCase(Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.id().toString()))
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Verdi"))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // The token issued before the change is still valid: its subject is the customer id
        getAs(customer.token(), "/customers/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.id().toString()))
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Verdi"))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        // From now on the customer logs in with the new address only
        login(newEmail, PASSWORD).andExpect(status().isOk());
        login(customer.email(), PASSWORD).andExpect(problem(401, "UNAUTHORIZED"));
    }

    @Test
    void emailAlreadyTaken() throws Exception {
        Customer anna = newCustomer();
        Customer customer = newCustomer();

        patchAs(customer.token(), "/customers/me", """
                {"email":"%s"}""".formatted(anna.email().toUpperCase(Locale.ROOT)))
                .andExpect(problem(409, "EMAIL_ALREADY_USED"));

        getAs(customer.token(), "/customers/me").andExpect(jsonPath("$.email").value(customer.email()));
    }

    @Test
    void invalidPersonalData() throws Exception {
        Customer customer = newCustomer();

        patchAs(customer.token(), "/customers/me", """
                {"email":"not-an-email"}""").andExpect(problem(400, "VALIDATION_ERROR"));
        patchAs(customer.token(), "/customers/me", """
                {"lastName":"  "}""").andExpect(problem(400, "VALIDATION_ERROR"));

        getAs(customer.token(), "/customers/me")
                .andExpect(jsonPath("$.email").value(customer.email()))
                .andExpect(jsonPath("$.lastName").value("Rossi"));
    }

    @Test
    void phoneNumberLongerThan20CharactersIsRejected() throws Exception {
        Customer customer = newCustomer();

        // FR-CUS-02: same rules as registration, where the phone number has at most 20 characters.
        // 21 here: the leading + and 20 digits
        patchAs(customer.token(), "/customers/me", """
                {"phoneNumber":"+12345678901234567890"}""").andExpect(problem(400, "VALIDATION_ERROR"));

        getAs(customer.token(), "/customers/me").andExpect(jsonPath("$.phoneNumber").value("+39 333 1234567"));
    }

    @Test
    void roleCannotBeChangedByTheCustomer() throws Exception {
        Customer customer = newCustomer();

        patchAs(customer.token(), "/customers/me", """
                {"role":"ADMIN","lastName":"Bianchi"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Bianchi"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        getAs(customer.token(), "/customers/me").andExpect(jsonPath("$.role").value("CUSTOMER"));
        // Not even a token issued after the request opens an admin-only endpoint
        String newToken = read(login(customer.email(), PASSWORD).andExpect(status().isOk()), "$.accessToken");
        getAs(newToken, "/customers").andExpect(problem(403, "FORBIDDEN"));
    }

    @Test
    void changePassword() throws Exception {
        Customer customer = newCustomer();
        String newPassword = "N3w-passwd";

        changePassword(customer, PASSWORD, newPassword)
                .andExpect(status().isNoContent());

        // Every session is revoked, and only the new password logs in
        refresh(customer.refreshToken()).andExpect(problem(401, "UNAUTHORIZED"));
        login(customer.email(), PASSWORD).andExpect(problem(401, "UNAUTHORIZED"));
        login(customer.email(), newPassword).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPassword() throws Exception {
        Customer customer = newCustomer();

        changePassword(customer, "wrong-password", "N3w-passwd")
                .andExpect(problem(400, "WRONG_CURRENT_PASSWORD"));

        // Nothing changed: same password, same session
        login(customer.email(), "N3w-passwd").andExpect(problem(401, "UNAUTHORIZED"));
        refresh(customer.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void invalidNewPassword() throws Exception {
        Customer customer = newCustomer();

        // BR-11: at least 8 characters, and different from the current one
        changePassword(customer, PASSWORD, "short").andExpect(problem(400, "VALIDATION_ERROR"));
        changePassword(customer, PASSWORD, PASSWORD).andExpect(problem(400, "VALIDATION_ERROR"));

        refresh(customer.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void adminListsCustomers() throws Exception {
        newCustomer();
        newCustomer();
        Customer admin = newAdmin();

        ResultActions response = getAs(admin.token(), "/customers?page=0&size=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.last").isBoolean())
                .andExpect(jsonPath("$.content[0].id").isNotEmpty())
                .andExpect(jsonPath("$.content[0].email").isNotEmpty())
                .andExpect(jsonPath("$.content[0].role").isNotEmpty())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        // At least the three customers above exist; the page is sorted by createdAt, newest first
        Number totalElements = read(response, "$.totalElements");
        assertThat(totalElements.longValue()).isGreaterThanOrEqualTo(3);
        List<String> createdAt = read(response, "$.content[*].createdAt");
        assertThat(createdAt).hasSizeBetween(3, 5);
        assertThat(createdAt.stream().map(Instant::parse)).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void listCustomersWithInvalidPaging() throws Exception {
        Customer admin = newAdmin();

        getAs(admin.token(), "/customers?page=-1").andExpect(problem(400, "VALIDATION_ERROR"));
        getAs(admin.token(), "/customers?size=0").andExpect(problem(400, "VALIDATION_ERROR"));
        getAs(admin.token(), "/customers?size=101").andExpect(problem(400, "VALIDATION_ERROR"));
    }

    @Test
    void customerCannotListCustomers() throws Exception {
        Customer customer = newCustomer();

        getAs(customer.token(), "/customers").andExpect(problem(403, "FORBIDDEN"));
    }

    @Test
    void adminEditsOnlyTheirOwnProfile() throws Exception {
        Customer admin = newAdmin();
        Customer customer = newCustomer();

        // FR-CUS-02 applies to the admin too, and never changes the role
        patchAs(admin.token(), "/customers/me", """
                {"firstName":"Grace","role":"CUSTOMER"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.id().toString()))
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        // FR-CUS-05: there is no route to edit another customer
        patchAs(admin.token(), "/customers/{id}", """
                {"lastName":"Hacked"}""", customer.id()).andExpect(problem(404, "NOT_FOUND"));

        getAs(customer.token(), "/customers/me").andExpect(jsonPath("$.lastName").value("Rossi"));
    }

    private ResultActions changePassword(Customer customer, String currentPassword, String newPassword)
            throws Exception {
        return send(HttpMethod.PUT, customer.token(), """
                {"currentPassword":"%s","newPassword":"%s"}""".formatted(currentPassword, newPassword),
                "/customers/me/password");
    }
}
