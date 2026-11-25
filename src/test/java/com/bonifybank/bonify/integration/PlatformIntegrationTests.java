package com.bonifybank.bonify.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Module M6 "Platform": API documentation (FR-PLT-01) and error format (FR-PLT-02).
 */
class PlatformIntegrationTests extends IntegrationTestSupport {

    @Test
    void apiDocsArePublicAndListEveryEndpoint() throws Exception {
        ResultActions response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        Map<String, Map<String, Object>> paths = read(response, "$.paths");
        List<String> operations = paths.entrySet().stream()
                .flatMap(path -> path.getValue().keySet().stream()
                        .map(method -> method.toUpperCase(Locale.ROOT) + " " + path.getKey()))
                .toList();
        // The API table of functional-analysis.md section 7, under /api/v1
        assertThat(operations).contains(
                "POST /api/v1/auth/register",
                "POST /api/v1/auth/login",
                "POST /api/v1/auth/refresh",
                "POST /api/v1/auth/logout",
                "GET /api/v1/customers/me",
                "PATCH /api/v1/customers/me",
                "PUT /api/v1/customers/me/password",
                "GET /api/v1/customers",
                "POST /api/v1/accounts",
                "GET /api/v1/accounts",
                "GET /api/v1/accounts/{iban}",
                "POST /api/v1/accounts/{iban}/deposit",
                "POST /api/v1/accounts/{iban}/withdraw",
                "GET /api/v1/accounts/{iban}/transactions",
                "PATCH /api/v1/accounts/{iban}/status",
                "POST /api/v1/transfers",
                "GET /api/v1/transfers/{id}");
    }

    @Test
    void apiDocsDeclareTheBearerScheme() throws Exception {
        // What makes Swagger UI show "Authorize" and send the access token on every call
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void unknownPathReturnsAProblemDetail() throws Exception {
        Customer customer = newCustomer();

        getAs(customer.token(), "/no-such-resource")
                .andExpect(problem(404, "NOT_FOUND"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(API + "/no-such-resource"));
        // Without a token the path is not even looked up
        getAs(null, "/no-such-resource").andExpect(problem(401, "UNAUTHORIZED"));
    }

    @Test
    void unsupportedMethodReturnsAProblemDetail() throws Exception {
        Customer customer = newCustomer();

        send(HttpMethod.DELETE, customer.token(), null, "/accounts")
                .andExpect(problem(405, "METHOD_NOT_ALLOWED"))
                .andExpect(header().string(HttpHeaders.ALLOW, allOf(containsString("GET"), containsString("POST"))));
    }

    @Test
    void unsupportedMediaTypeOrMalformedBodyReturnsAProblemDetail() throws Exception {
        Customer customer = newCustomer();
        String iban = openAccount(customer);

        mockMvc.perform(post(API + "/accounts/{iban}/deposit", iban)
                .header(HttpHeaders.AUTHORIZATION, bearer(customer.token()))
                .contentType(MediaType.TEXT_PLAIN)
                .content("amount=10.00"))
                .andExpect(problem(415, "UNSUPPORTED_MEDIA_TYPE"));
        postAs(customer.token(), "/accounts/{iban}/deposit", "{not json", iban)
                .andExpect(problem(400, "VALIDATION_ERROR"));
    }
}
