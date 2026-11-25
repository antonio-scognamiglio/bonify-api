package com.bonifybank.bonify.modules.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.SecurityConfig;
import com.bonifybank.bonify.config.WebConfig;
import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.customer.dto.ChangePasswordRequest;
import com.bonifybank.bonify.modules.customer.dto.CustomerResponse;
import com.bonifybank.bonify.modules.customer.dto.UpdateProfileRequest;

/**
 * FR-CUS-01..05 at the web layer. Role rules are covered by RoutePermissionsTests.
 */
@WebMvcTest(CustomerController.class)
@Import({ WebConfig.class, SecurityConfig.class, JwtConfig.class })
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class CustomerControllerTests {

    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String ME = CUSTOMERS + "/me";
    private static final String PASSWORD = ME + "/password";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerService customerService;

    @Test
    void getMeReturnsProfileWithoutPassword() throws Exception {
        when(customerService.getMe()).thenReturn(customerResponse());

        mockMvc.perform(get(ME).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.lastName").value("Rossi"))
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void updateMePassesOnlyTheSentFields() throws Exception {
        when(customerService.updateMe(any())).thenReturn(customerResponse());

        mockMvc.perform(updateMe("{\"firstName\":\"Anna Maria\"}"))
                .andExpect(status().isOk());

        verify(customerService).updateMe(new UpdateProfileRequest("Anna Maria", null, null, null));
    }

    @Test
    void updateMeIgnoresRoleAndPassword() throws Exception {
        when(customerService.updateMe(any())).thenReturn(customerResponse());

        mockMvc.perform(updateMe("{\"firstName\":\"Anna\",\"role\":\"ADMIN\",\"password\":\"x\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateProfileRequest> captured = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(customerService).updateMe(captured.capture());
        assertThat(captured.getValue()).isEqualTo(new UpdateProfileRequest("Anna", null, null, null));
        assertThat(UpdateProfileRequest.class.getRecordComponents())
                .extracting("name").containsExactly("firstName", "lastName", "email", "phoneNumber");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"email\":\"not-an-email\"}", "{\"email\":\"\"}", "{\"firstName\":\"\"}", "{\"firstName\":\"   \"}",
            "{\"firstName\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}", "{\"lastName\":\" \"}",
            "{\"phoneNumber\":\"abc\"}", "{\"phoneNumber\":\"123456\"}" })
    void invalidProfileUpdateIsValidationError(String body) throws Exception {
        mockMvc.perform(updateMe(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(customerService);
    }

    // Section 8: a validation error carries the list of the invalid fields
    @Test
    void validationErrorListsTheInvalidFields() throws Exception {
        mockMvc.perform(updateMe("{\"email\":\"not-an-email\",\"phoneNumber\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "phoneNumber")))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        mockMvc.perform(get(CUSTOMERS + "?size=101").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("size")));
    }

    @Test
    void emailAlreadyUsedIs409() throws Exception {
        when(customerService.updateMe(any()))
                .thenThrow(new BusinessException(ErrorCodes.EMAIL_ALREADY_USED, "Email already in use"));

        mockMvc.perform(updateMe("{\"email\":\"taken@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
                // The field list belongs to validation errors only
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void changePasswordReturnsNoContent() throws Exception {
        mockMvc.perform(changePassword("{\"currentPassword\":\"oldpassword\",\"newPassword\":\"newpassword\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(customerService).changeMyPassword(new ChangePasswordRequest("oldpassword", "newpassword"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"newPassword\":\"newpassword\"}", "{\"currentPassword\":\"oldpassword\"}",
            "{\"currentPassword\":\"oldpassword\",\"newPassword\":\"1234567\"}" })
    void invalidPasswordChangeIsValidationError(String body) throws Exception {
        mockMvc.perform(changePassword(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(customerService);
    }

    @Test
    void wrongCurrentPasswordIs400() throws Exception {
        doThrow(new BusinessException(ErrorCodes.WRONG_CURRENT_PASSWORD, "Current password is wrong"))
                .when(customerService).changeMyPassword(any());

        mockMvc.perform(changePassword("{\"currentPassword\":\"wrongpass\",\"newPassword\":\"newpassword\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));
    }

    @Test
    void listUsesDefaultPaging() throws Exception {
        when(customerService.listCustomers(anyInt(), anyInt())).thenReturn(page(0, 20));

        mockMvc.perform(get(CUSTOMERS).with(admin()))
                .andExpect(status().isOk());

        verify(customerService).listCustomers(0, 20);
    }

    @Test
    void listPassesExplicitPaging() throws Exception {
        when(customerService.listCustomers(anyInt(), anyInt())).thenReturn(page(2, 5));

        mockMvc.perform(get(CUSTOMERS + "?page=2&size=5").with(admin()))
                .andExpect(status().isOk());

        verify(customerService).listCustomers(2, 5);
    }

    @Test
    void listResponseHasExactlyThePagedShape() throws Exception {
        when(customerService.listCustomers(anyInt(), anyInt())).thenReturn(page(0, 20));

        mockMvc.perform(get(CUSTOMERS).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "page=-1", "size=0", "size=101", "page=abc" })
    void invalidPagingIsValidationError(String query) throws Exception {
        mockMvc.perform(get(CUSTOMERS + "?" + query).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(customerService);
    }

    @Test
    void noRouteEditsAnotherCustomer() throws Exception {
        String other = CUSTOMERS + "/" + UUID.randomUUID();

        // Observed behaviour: PATCH/PUT/DELETE on /customers/{id} are not mapped at all
        mockMvc.perform(patch(other).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(other).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(other).with(admin()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(customerService);
    }

    private static MockHttpServletRequestBuilder updateMe(String body) {
        return patch(ME).with(customer()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder changePassword(
            String body) {
        return put(PASSWORD).with(customer()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor customer() {
        return user("customer").roles("CUSTOMER");
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private static PageImpl<CustomerResponse> page(int page, int size) {
        return new PageImpl<>(List.of(customerResponse()), PageRequest.of(page, size), 1);
    }

    private static CustomerResponse customerResponse() {
        return new CustomerResponse(UUID.randomUUID(), "anna@example.com", "Anna", "Rossi", "+393401234567",
                "RSSNNA90A41H501N", "CUSTOMER", Instant.now(), Instant.now());
    }
}
