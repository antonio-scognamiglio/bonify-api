package com.bonifybank.bonify.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.bonifybank.bonify.modules.customer.CustomerController;

/**
 * Security-filter errors (401/403) are returned as RFC 7807 ProblemDetail,
 * not plain Spring defaults.
 */
@WebMvcTest(CustomerController.class)
@Import({ SecurityConfig.class, JwtConfig.class })
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class SecurityErrorFormatTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void guestOnProtectedRouteIsProblemDetail401() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().is(401))
                .andExpect(header().string("Content-Type", equalTo("application/problem+json")))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void customerWithoutAdminRoleIsProblemDetail403() throws Exception {
        mockMvc.perform(get("/api/v1/customers").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().is(403))
                .andExpect(header().string("Content-Type", equalTo("application/problem+json")))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }
}
