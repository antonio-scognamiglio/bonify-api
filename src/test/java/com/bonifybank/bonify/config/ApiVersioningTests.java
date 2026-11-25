package com.bonifybank.bonify.config;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bonifybank.bonify.modules.customer.CustomerController;
import com.bonifybank.bonify.modules.customer.CustomerService;

@WebMvcTest(CustomerController.class)
@Import(WebConfig.class)
@AutoConfigureMockMvc(addFilters = false) // we test routing, not security
class ApiVersioningTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerService customerService;

    @BeforeEach
    void stubService() {
        when(customerService.listCustomers(anyInt(), anyInt())).thenReturn(Page.empty());
    }

    @Test
    void v1IsRoutedToController() throws Exception {
        // 200: the route exists and is reached (the service is mocked)
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isOk());
    }

    @Test
    void unsupportedVersionIsRejected() throws Exception {
        mockMvc.perform(get("/api/v9/customers")).andExpect(status().isBadRequest());
    }

    @Test
    void versionWithoutVPrefixIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/1/customers")).andExpect(status().isNotFound());
    }

    @Test
    void oldUnversionedPathNoLongerExists() throws Exception {
        mockMvc.perform(get("/api/customers")).andExpect(status().is4xxClientError());
    }
}
