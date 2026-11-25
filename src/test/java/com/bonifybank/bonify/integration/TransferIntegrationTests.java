package com.bonifybank.bonify.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Feature "Transfers" of the acceptance criteria (functional-analysis.md section 10):
 * FR-TRF-01 / 02 / 03, BR-03 / 04 / 05 / 07 / 08 / 09 and NFR-03 (100 concurrent transfers).
 */
class TransferIntegrationTests extends IntegrationTestSupport {

    private static final String DESCRIPTION = "November rent";

    @Test
    void successfulTransfer() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);

        ResultActions response = transfer(anna, newKey(), a, b, "150.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromIban").value(a))
                .andExpect(jsonPath("$.toIban").value(b))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.description").value(DESCRIPTION))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        String transferId = read(response, "$.id");

        assertThat(balance(anna, a)).isEqualByComparingTo("350.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("150.00");
        // One line per side, linked to the same transfer and visible in both histories
        Map<String, Object> outgoing = ledger(anna, a).getFirst();
        assertThat(outgoing).containsEntry("type", "TRANSFER_OUT").containsEntry("transferId", transferId)
                .containsEntry("description", DESCRIPTION);
        assertThat(money(outgoing.get("amount"))).isEqualByComparingTo("150.00");
        assertThat(money(outgoing.get("balanceAfter"))).isEqualByComparingTo("350.00");
        List<Map<String, Object>> incomingLines = ledger(bruno, b);
        assertThat(incomingLines).hasSize(1);
        assertThat(incomingLines.getFirst()).containsEntry("type", "TRANSFER_IN")
                .containsEntry("transferId", transferId).containsEntry("description", DESCRIPTION);
        assertThat(money(incomingLines.getFirst().get("amount"))).isEqualByComparingTo("150.00");
        assertThat(money(incomingLines.getFirst().get("balanceAfter"))).isEqualByComparingTo("150.00");
    }

    @Test
    void newTransferIsReturnedWithItsCreationTime() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);

        ResultActions created = transfer(anna, newKey(), a, b, "150.00").andExpect(status().isCreated());

        // Section 7.1: the 201 response already carries createdAt, the value the transfer shows afterwards
        String transferId = read(created, "$.id");
        String createdAt = read(getAs(anna.token(), "/transfers/{id}", transferId).andExpect(status().isOk()),
                "$.createdAt");
        assertThat(createdAt).isNotEmpty();
        created.andExpect(jsonPath("$.createdAt").value(createdAt));
    }

    @Test
    void insufficientFunds() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "100.00");
        String b = openAccount(bruno);

        transfer(anna, newKey(), a, b, "1000.00")
                .andExpect(problem(422, "INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.type").value(endsWith("/errors/insufficient-funds")))
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(API + "/transfers"));

        assertThat(balance(anna, a)).isEqualByComparingTo("100.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("0.00");
        // Only the initial deposit on A, nothing on B
        assertThat(ledger(anna, a)).hasSize(1);
        assertThat(ledger(bruno, b)).isEmpty();
    }

    @Test
    void idempotentRetry() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);
        String key = newKey();
        String transferId = read(transfer(anna, key, a, b, "150.00").andExpect(status().isCreated()), "$.id");

        transfer(anna, key, a, b, "150.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId))
                .andExpect(jsonPath("$.fromIban").value(a))
                .andExpect(jsonPath("$.toIban").value(b))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(balance(anna, a)).isEqualByComparingTo("350.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("150.00");
        // Deposit + one TRANSFER_OUT on A, one TRANSFER_IN on B: the retry created no line
        assertThat(ledger(anna, a)).hasSize(2);
        assertThat(ledger(bruno, b)).hasSize(1);
    }

    @Test
    void reusedKeyWithADifferentBody() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);
        String key = newKey();
        transfer(anna, key, a, b, "150.00").andExpect(status().isCreated());

        transfer(anna, key, a, b, "200.00").andExpect(problem(409, "IDEMPOTENCY_KEY_REUSED"));

        assertThat(balance(anna, a)).isEqualByComparingTo("350.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("150.00");
    }

    @Test
    void sameKeyFromAnotherCustomer() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        Customer carla = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);
        String c = fundedAccount(carla, "500.00");
        String key = newKey();
        transfer(anna, key, a, b, "150.00").andExpect(status().isCreated());

        // Same body: the original transfer is not returned to someone who did not send it
        transfer(carla, key, a, b, "150.00").andExpect(problem(409, "IDEMPOTENCY_KEY_REUSED"));
        // Her own transfer with that key is refused as well
        transfer(carla, key, c, b, "150.00").andExpect(problem(409, "IDEMPOTENCY_KEY_REUSED"));

        assertThat(balance(anna, a)).isEqualByComparingTo("350.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("150.00");
        assertThat(balance(carla, c)).isEqualByComparingTo("500.00");
    }

    @Test
    void rejectedRequestDoesNotConsumeTheKey() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "100.00");
        String b = openAccount(bruno);
        String key = newKey();
        transfer(anna, key, a, b, "150.00").andExpect(problem(422, "INSUFFICIENT_FUNDS"));

        // The problem is fixed: the same request with the same key now goes through
        deposit(anna, a, "100.00").andExpect(status().isOk());
        transfer(anna, key, a, b, "150.00").andExpect(status().isCreated());

        assertThat(balance(anna, a)).isEqualByComparingTo("50.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("150.00");
    }

    @Test
    void blockedAccount() throws Exception {
        Customer admin = newAdmin();
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = fundedAccount(bruno, "20.00");
        setStatus(admin, b, "BLOCKED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));

        // BR-07: a blocked account can neither receive nor send money, by transfer or in cash
        transfer(anna, newKey(), a, b, "150.00").andExpect(problem(422, "ACCOUNT_BLOCKED"));
        transfer(bruno, newKey(), b, a, "10.00").andExpect(problem(422, "ACCOUNT_BLOCKED"));
        deposit(bruno, b, "10.00").andExpect(problem(422, "ACCOUNT_BLOCKED"));
        // It keeps its balance and history (FR-ACC-06)
        assertThat(balance(anna, a)).isEqualByComparingTo("500.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("20.00");
        assertThat(ledger(bruno, b)).hasSize(1);

        setStatus(admin, b, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        transfer(anna, newKey(), a, b, "150.00").andExpect(status().isCreated());
        deposit(bruno, b, "10.00").andExpect(status().isOk());
        assertThat(balance(anna, a)).isEqualByComparingTo("350.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("180.00");
    }

    @Test
    void sameAccount() throws Exception {
        Customer anna = newCustomer();
        String a = fundedAccount(anna, "500.00");

        transfer(anna, newKey(), a, a, "150.00").andExpect(problem(400, "SAME_ACCOUNT"));

        assertThat(balance(anna, a)).isEqualByComparingTo("500.00");
    }

    @Test
    void unknownIban() throws Exception {
        Customer anna = newCustomer();
        String a = fundedAccount(anna, "500.00");

        transfer(anna, newKey(), a, UNKNOWN_IBAN, "150.00").andExpect(problem(404, "NOT_FOUND"));
        transfer(anna, newKey(), UNKNOWN_IBAN, a, "150.00").andExpect(problem(404, "NOT_FOUND"));

        assertThat(balance(anna, a)).isEqualByComparingTo("500.00");
    }

    @Test
    void notOwnerOfTheSource() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);

        // Bruno tries to pull money from Anna's account into his own
        transfer(bruno, newKey(), a, b, "150.00").andExpect(problem(403, "FORBIDDEN"));

        assertThat(balance(anna, a)).isEqualByComparingTo("500.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("0.00");
    }

    @Test
    void missingOrInvalidIdempotencyKey() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);

        transfer(anna, null, a, b, "150.00").andExpect(problem(400, "VALIDATION_ERROR"));
        transfer(anna, "not-a-uuid", a, b, "150.00").andExpect(problem(400, "VALIDATION_ERROR"));

        assertThat(balance(anna, a)).isEqualByComparingTo("500.00");
    }

    @Test
    void invalidAmount() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);

        // BR-01: greater than zero, at most 2 decimal places
        transfer(anna, newKey(), a, b, "0").andExpect(problem(400, "VALIDATION_ERROR"));
        transfer(anna, newKey(), a, b, "-10.00").andExpect(problem(400, "VALIDATION_ERROR"));
        transfer(anna, newKey(), a, b, "10.001").andExpect(problem(400, "VALIDATION_ERROR"));

        assertThat(balance(anna, a)).isEqualByComparingTo("500.00");
    }

    @Test
    void viewTransfer() throws Exception {
        Customer admin = newAdmin();
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        Customer carla = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);
        String transferId = read(transfer(anna, newKey(), a, b, "150.00").andExpect(status().isCreated()), "$.id");

        // FR-TRF-03: sender, receiver and admin see it
        for (Customer viewer : List.of(anna, bruno, admin)) {
            getAs(viewer.token(), "/transfers/{id}", transferId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(transferId))
                    .andExpect(jsonPath("$.fromIban").value(a))
                    .andExpect(jsonPath("$.toIban").value(b))
                    .andExpect(jsonPath("$.amount").value(150.00))
                    .andExpect(jsonPath("$.description").value(DESCRIPTION))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }
        getAs(carla.token(), "/transfers/{id}", transferId).andExpect(problem(403, "FORBIDDEN"));
        getAs(anna.token(), "/transfers/{id}", UUID.randomUUID()).andExpect(problem(404, "NOT_FOUND"));
        getAs(null, "/transfers/{id}", transferId).andExpect(problem(401, "UNAUTHORIZED"));
    }

    @Test
    void concurrentTransfers() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "1000.00");
        String b = fundedAccount(bruno, "1000.00");
        // NFR-03 / BR-05: 100 transfers of 10.00, 50 in each direction, each with its own key
        List<Callable<ResultActions>> calls = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            calls.add(() -> transfer(anna, newKey(), a, b, "10.00"));
            calls.add(() -> transfer(bruno, newKey(), b, a, "10.00"));
        }

        List<ResultActions> responses = inParallel(calls);

        // All completed in time and none failed: no deadlock, no lost request
        assertThat(statuses(responses)).hasSize(100).containsOnly(201);
        BigDecimal balanceA = balance(anna, a);
        BigDecimal balanceB = balance(bruno, b);
        assertThat(balanceA.add(balanceB)).isEqualByComparingTo("2000.00");
        assertThat(balanceA).isNotNegative();
        assertThat(balanceB).isNotNegative();
        // The initial deposit plus 50 outgoing and 50 incoming lines on each account
        assertThat(ledger(anna, a)).hasSize(101);
        assertThat(ledger(bruno, b)).hasSize(101);
        assertLedgerMatchesBalance(anna, a);
        assertLedgerMatchesBalance(bruno, b);
    }

    @Test
    void concurrentRetriesWithTheSameKey() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String a = fundedAccount(anna, "500.00");
        String b = openAccount(bruno);
        String key = newKey();

        List<ResultActions> responses = inParallel(
                Collections.nCopies(10, () -> transfer(anna, key, a, b, "150.00")));

        // BR-04: executed once (201); every other request gets the original transfer (200)
        assertThat(statuses(responses)).containsOnlyOnce(201).filteredOn(status -> status != 201)
                .hasSize(9).containsOnly(200);
        List<String> transferIds = new ArrayList<>();
        for (ResultActions response : responses) {
            transferIds.add(read(response, "$.id"));
        }
        assertThat(transferIds).containsOnly(transferIds.getFirst());
        assertThat(balance(anna, a)).isEqualByComparingTo("350.00");
        assertThat(balance(bruno, b)).isEqualByComparingTo("150.00");
        assertThat(ledger(anna, a)).hasSize(2);
        assertThat(ledger(bruno, b)).hasSize(1);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** POST /transfers as the sender; no Idempotency-Key header when the key is null. */
    private ResultActions transfer(Customer sender, String key, String fromIban, String toIban, String amount)
            throws Exception {
        MockHttpServletRequestBuilder request = post(API + "/transfers")
                .header(HttpHeaders.AUTHORIZATION, bearer(sender.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fromIban":"%s","toIban":"%s","amount":%s,"description":"%s"}"""
                        .formatted(fromIban, toIban, amount, DESCRIPTION));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }
}
