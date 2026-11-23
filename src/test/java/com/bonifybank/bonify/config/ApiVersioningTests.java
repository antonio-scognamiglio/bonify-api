package com.bonifybank.bonify.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.bonifybank.bonify.modules.customer.CustomerController;

@WebMvcTest(CustomerController.class)
@Import(WebConfig.class)
@AutoConfigureMockMvc(addFilters = false) // verifichiamo il routing, non la security
class ApiVersioningTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void v1IsRoutedToController() throws Exception {
        // 501: la rotta esiste ed e' raggiunta, ma non e' ancora implementata
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isNotImplemented());
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
