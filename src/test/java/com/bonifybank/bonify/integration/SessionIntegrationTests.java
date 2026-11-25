package com.bonifybank.bonify.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.JwtProperties;
import com.jayway.jsonpath.JsonPath;

/**
 * Registration, login and feature "Session" of the acceptance criteria (functional-analysis.md
 * section 10): FR-AUTH-01 ... 06.
 */
class SessionIntegrationTests extends IntegrationTestSupport {

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    TransactionTemplate transactionTemplate;

    // ---- Registration and login ------------------------------------------------------------------

    @Test
    void registration() throws Exception {
        String email = newEmail();
        String taxCode = newTaxCode();

        // Sent with the "wrong" case: email is stored in lower case, tax code in upper case
        ResultActions response = register(email.toUpperCase(Locale.ROOT), taxCode.toLowerCase(Locale.ROOT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Rossi"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneNumber").value("+39 333 1234567"))
                .andExpect(jsonPath("$.taxCode").value(taxCode))
                .andExpect(jsonPath("$.password").doesNotExist());

        // NFR-01: the stored password is a BCrypt hash of the one sent, never the clear text
        String id = read(response, "$.id");
        String storedPassword = customerRepository.findById(UUID.fromString(id)).orElseThrow().getPassword();
        assertThat(storedPassword).matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
        assertThat(passwordEncoder.matches(PASSWORD, storedPassword)).isTrue();
        // A registered customer is always a CUSTOMER
        String token = read(login(email, PASSWORD).andExpect(status().isOk()), "$.accessToken");
        getAs(token, "/customers/me").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void registrationWithAnEmailAlreadyUsedInAnotherCase() throws Exception {
        Customer existing = newCustomer();

        register(existing.email().toUpperCase(Locale.ROOT), newTaxCode())
                .andExpect(problem(409, "EMAIL_ALREADY_USED"));
    }

    @Test
    void registrationWithATaxCodeAlreadyUsed() throws Exception {
        String taxCode = newTaxCode();
        register(newEmail(), taxCode).andExpect(status().isCreated());

        register(newEmail(), taxCode.toLowerCase(Locale.ROOT)).andExpect(problem(409, "TAX_CODE_ALREADY_USED"));
    }

    @Test
    void concurrentRegistrationsWithTheSameEmail() throws Exception {
        String email = newEmail();

        List<ResultActions> responses = inParallel(Collections.nCopies(5, () -> register(email, newTaxCode())));

        // BR-10: one registration wins and the others are told that the email is taken
        assertThat(statuses(responses)).containsExactlyInAnyOrder(201, 409, 409, 409, 409);
        for (ResultActions refused : withStatus(responses, 409)) {
            refused.andExpect(problem(409, "EMAIL_ALREADY_USED"));
        }
    }

    @Test
    void concurrentRegistrationsWithTheSameTaxCode() throws Exception {
        String taxCode = newTaxCode();

        List<ResultActions> responses = inParallel(Collections.nCopies(5, () -> register(newEmail(), taxCode)));

        assertThat(statuses(responses)).containsExactlyInAnyOrder(201, 409, 409, 409, 409);
        for (ResultActions refused : withStatus(responses, 409)) {
            refused.andExpect(problem(409, "TAX_CODE_ALREADY_USED"));
        }
    }

    @Test
    void registrationCannotCreateAnAdmin() throws Exception {
        String email = newEmail();

        // Section 2: admins cannot be created through the public API, a "role" in the body is ignored
        postAs(null, "/auth/register", """
                {"firstName":"Mario","lastName":"Rossi","email":"%s","phoneNumber":"+39 333 1234567",
                 "taxCode":"%s","password":"%s","role":"ADMIN"}""".formatted(email, newTaxCode(), PASSWORD))
                .andExpect(status().isCreated());

        String token = read(login(email, PASSWORD).andExpect(status().isOk()), "$.accessToken");
        getAs(token, "/customers/me").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        getAs(token, "/customers").andExpect(problem(403, "FORBIDDEN"));
    }

    @Test
    void registrationWithInvalidInput() throws Exception {
        register("not-an-email", newTaxCode()).andExpect(problem(400, "VALIDATION_ERROR"));
        register(newEmail(), "NOT-A-TAX-CODE").andExpect(problem(400, "VALIDATION_ERROR"));
    }

    @Test
    void loginReturnsTheTokenPair() throws Exception {
        Customer customer = newCustomer();

        // The email is not case-sensitive (FR-AUTH-01)
        ResultActions response = login(customer.email().toUpperCase(Locale.ROOT), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800));

        // The access token is a JWT whose subject is the customer id, with the role as a claim
        String accessToken = read(response, "$.accessToken");
        String claims = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(claims, "$.sub")).isEqualTo(customer.id().toString());
        assertThat(JsonPath.<String>read(claims, "$.role")).isEqualTo("CUSTOMER");
        getAs(accessToken, "/customers/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.id().toString()));
        // The refresh token is opaque and only its SHA-256 hash is stored
        String refreshToken = read(response, "$.refreshToken");
        assertThat(refreshToken).doesNotContain(".");
        assertThat(jdbc.queryForList("select token_hash from refresh_tokens where customer_id = ?", String.class,
                customer.id())).isNotEmpty().allMatch(hash -> hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameResponse() throws Exception {
        Customer customer = newCustomer();

        ResultActions wrongPassword = login(customer.email(), "wrong-password")
                .andExpect(problem(401, "UNAUTHORIZED"));
        ResultActions unknownEmail = login(newEmail(), PASSWORD)
                .andExpect(problem(401, "UNAUTHORIZED"));

        // Nothing but the timestamp tells the two cases apart, so existing emails are not revealed
        assertThat(withoutTimestamp(unknownEmail)).isEqualTo(withoutTimestamp(wrongPassword));
    }

    @Test
    void protectedEndpointWithoutToken() throws Exception {
        getAs(null, "/customers/me")
                .andExpect(problem(401, "UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value(API + "/customers/me"));
    }

    @Test
    void protectedEndpointWithAnInvalidOrExpiredToken() throws Exception {
        Customer customer = newCustomer();

        // Section 8: 401 UNAUTHORIZED in the RFC 7807 format, like for a missing token
        getAs("not-a-jwt", "/customers/me").andExpect(problem(401, "UNAUTHORIZED"));
        getAs(expiredAccessToken(customer), "/customers/me").andExpect(problem(401, "UNAUTHORIZED"));
        // The refresh token is not an access token
        getAs(customer.refreshToken(), "/customers/me").andExpect(problem(401, "UNAUTHORIZED"));
    }

    @Test
    void concurrentLoginsLeaveOneActiveSession() throws Exception {
        Customer customer = newCustomer();

        List<ResultActions> logins = inParallel(Collections.nCopies(5, () -> login(customer.email(), PASSWORD)));

        assertThat(statuses(logins)).containsOnly(200);
        // FR-AUTH-02: one active session per customer, so only one of the refresh tokens can be used
        List<Integer> refreshStatuses = new ArrayList<>();
        for (ResultActions login : logins) {
            String refreshToken = read(login, "$.refreshToken");
            refreshStatuses.add(refresh(refreshToken).andReturn().getResponse().getStatus());
        }
        assertThat(refreshStatuses).containsExactlyInAnyOrder(200, 401, 401, 401, 401);
    }

    // ---- Session ---------------------------------------------------------------------------------

    @Test
    void refreshWithRotation() throws Exception {
        Customer customer = newCustomer();
        String r1 = customer.refreshToken();

        ResultActions response = refresh(r1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800));

        String newAccessToken = read(response, "$.accessToken");
        String r2 = read(response, "$.refreshToken");
        assertThat(newAccessToken).isNotEqualTo(customer.token());
        assertThat(r2).isNotEqualTo(r1);
        getAs(newAccessToken, "/customers/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.id().toString()));
        refresh(r1).andExpect(problem(401, "UNAUTHORIZED"));
    }

    @Test
    void expiredAccessTokenOnRefresh() throws Exception {
        Customer customer = newCustomer();
        String expired = expiredAccessToken(customer);
        // The token is really rejected where it matters
        getAs(expired, "/customers/me").andExpect(status().isUnauthorized());

        // FR-AUTH-03: the Authorization header is ignored on the public auth endpoints
        ResultActions response = refreshWithAuthorization(customer.refreshToken(), bearer(expired))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        String r2 = read(response, "$.refreshToken");
        refreshWithAuthorization(r2, "Bearer not-a-jwt").andExpect(status().isOk());
    }

    @Test
    void reuseOfARotatedToken() throws Exception {
        Customer customer = newCustomer();
        String r1 = customer.refreshToken();
        String r2 = read(refresh(r1).andExpect(status().isOk()), "$.refreshToken");

        refresh(r1).andExpect(problem(401, "UNAUTHORIZED"));

        // FR-AUTH-06: the whole session is revoked, the customer has to log in again
        refresh(r2).andExpect(problem(401, "UNAUTHORIZED"));
        login(customer.email(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void loginOnAnotherDevice() throws Exception {
        Customer deviceA = newCustomer();

        String deviceBRefreshToken = read(login(deviceA.email(), PASSWORD).andExpect(status().isOk()),
                "$.refreshToken");

        // One active session per customer: device A can no longer refresh, device B can
        refresh(deviceA.refreshToken()).andExpect(problem(401, "UNAUTHORIZED"));
        refresh(deviceBRefreshToken).andExpect(status().isOk());
        // The access token of device A stays valid until it expires (stateless API, NFR-02)
        getAs(deviceA.token(), "/customers/me").andExpect(status().isOk());
    }

    @Test
    void logout() throws Exception {
        Customer customer = newCustomer();

        logout(customer.refreshToken()).andExpect(status().isNoContent());

        refresh(customer.refreshToken()).andExpect(problem(401, "UNAUTHORIZED"));
        // Idempotent: an already revoked or unknown token is ignored
        logout(customer.refreshToken()).andExpect(status().isNoContent());
        logout("unknown-refresh-token").andExpect(status().isNoContent());
    }

    @Test
    void refreshWithAMissingOrUnknownToken() throws Exception {
        postAs(null, "/auth/refresh", "{}").andExpect(problem(400, "VALIDATION_ERROR"));
        refresh("unknown-refresh-token").andExpect(problem(401, "UNAUTHORIZED"));
    }

    @Test
    void concurrentRefreshWithTheSameToken() throws Exception {
        Customer customer = newCustomer();
        int requests = 5;
        // The token row is kept locked until every request has read the token as still valid and is
        // waiting to mark it as used: this makes the requests really overlap. A request arriving after
        // the winner has committed is not concurrent any more: it is a reuse (FR-AUTH-06)
        CountDownLatch tokenLocked = new CountDownLatch(1);
        CompletableFuture<Void> lockHolder = CompletableFuture.runAsync(
                () -> transactionTemplate.executeWithoutResult(transaction -> {
                    jdbc.queryForList("select id from refresh_tokens where customer_id = ? for update", UUID.class,
                            customer.id());
                    tokenLocked.countDown();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    while (requestsWaitingForThisTransaction() < requests) {
                        if (System.nanoTime() > deadline) {
                            throw new IllegalStateException("The refresh requests did not reach the token row");
                        }
                    }
                }));
        assertThat(tokenLocked.await(30, TimeUnit.SECONDS)).isTrue();

        List<ResultActions> responses = inParallel(
                Collections.nCopies(requests, () -> refresh(customer.refreshToken())));
        lockHolder.get(30, TimeUnit.SECONDS);

        // FR-AUTH-04: marking the token as used is atomic, only one request wins
        assertThat(statuses(responses)).containsExactlyInAnyOrder(200, 401, 401, 401, 401);
        // The losers did not revoke the session: the new refresh token of the winner still works
        String winnerRefreshToken = read(withStatus(responses, 200).getFirst(), "$.refreshToken");
        refresh(winnerRefreshToken).andExpect(status().isOk());
    }

    /** Number of database sessions queued, directly or not, behind a lock of the current transaction. */
    private int requestsWaitingForThisTransaction() {
        return jdbc.queryForObject("""
                with recursive waiting(pid) as (
                    select pid from pg_stat_activity where pg_backend_pid() = any(pg_blocking_pids(pid))
                    union
                    select a.pid from pg_stat_activity a join waiting w on w.pid = any(pg_blocking_pids(a.pid))
                )
                select count(*) from waiting""", Integer.class);
    }

    /** An access token of the customer, correctly signed, that expired one hour ago. */
    private String expiredAccessToken(Customer customer) {
        Instant expiry = Instant.now().minus(1, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(customer.id().toString())
                .claim(JwtConfig.ROLE_CLAIM, "CUSTOMER")
                .issuedAt(expiry.minus(jwtProperties.expiration()))
                .expiresAt(expiry)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private ResultActions refreshWithAuthorization(String refreshToken, String authorization) throws Exception {
        return mockMvc.perform(post(API + "/auth/refresh")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}""".formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return postAs(null, "/auth/logout", """
                {"refreshToken":"%s"}""".formatted(refreshToken));
    }

    private static Map<String, Object> withoutTimestamp(ResultActions response) throws Exception {
        Map<String, Object> body = read(response, "$");
        body.remove("timestamp");
        return body;
    }
}
