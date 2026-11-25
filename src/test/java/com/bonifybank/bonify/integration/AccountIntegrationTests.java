package com.bonifybank.bonify.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Accounts, cash operations, transaction history and feature "Ownership" of the acceptance criteria
 * (functional-analysis.md section 10): FR-ACC-01 ... 05, FR-TRX-01 / 02, BR-01 / 02 / 05 / 06.
 */
class AccountIntegrationTests extends IntegrationTestSupport {

    private static final String HISTORY = "/accounts/{iban}/transactions";

    @Test
    void openAccount() throws Exception {
        Customer customer = newCustomer();

        ResultActions response = postAs(customer.token(), "/accounts", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iban").isNotEmpty())
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // A second account gets another IBAN
        String iban = read(response, "$.iban");
        assertThat(openAccount(customer)).isNotEqualTo(iban);
        getAs(customer.token(), "/accounts/{iban}", iban)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(iban))
                .andExpect(jsonPath("$.balance").value(0.00));
        assertThat(ledger(customer, iban)).isEmpty();
    }

    @Test
    void newAccountIsReturnedWithItsCreationTime() throws Exception {
        Customer customer = newCustomer();

        ResultActions created = postAs(customer.token(), "/accounts", null).andExpect(status().isCreated());

        // FR-ACC-01: 201 "with the account", so with the createdAt the account shows afterwards
        String iban = read(created, "$.iban");
        String createdAt = read(getAs(customer.token(), "/accounts/{iban}", iban).andExpect(status().isOk()),
                "$.createdAt");
        assertThat(createdAt).isNotEmpty();
        created.andExpect(jsonPath("$.createdAt").value(createdAt));
    }

    @Test
    void listOwnAccounts() throws Exception {
        Customer anna = newCustomer();
        Customer bruno = newCustomer();
        String first = fundedAccount(anna, "25.50");
        String second = openAccount(anna);
        openAccount(bruno);

        ResultActions response = getAs(anna.token(), "/accounts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.iban == '%s')].balance".formatted(first)).value(25.50))
                .andExpect(jsonPath("$[?(@.iban == '%s')].balance".formatted(second)).value(0.00))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());

        List<String> ibans = read(response, "$[*].iban");
        assertThat(ibans).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void accessToAnotherCustomersAccount() throws Exception {
        Customer x = newCustomer();
        Customer y = newCustomer();
        String a = fundedAccount(x, "100.00");

        // BR-06: Y can neither see nor operate on the account of X
        getAs(y.token(), "/accounts/{iban}", a).andExpect(problem(403, "FORBIDDEN"));
        getAs(y.token(), HISTORY, a).andExpect(problem(403, "FORBIDDEN"));
        deposit(y, a, "10.00").andExpect(problem(403, "FORBIDDEN"));
        withdraw(y, a, "10.00").andExpect(problem(403, "FORBIDDEN"));

        assertThat(balance(x, a)).isEqualByComparingTo("100.00");
        assertThat(ledger(x, a)).hasSize(1);
    }

    @Test
    void adminViewsAnyAccountButCannotMoveMoney() throws Exception {
        Customer admin = newAdmin();
        Customer customer = newCustomer();
        String iban = fundedAccount(customer, "100.00");

        getAs(admin.token(), "/accounts/{iban}", iban)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(iban))
                .andExpect(jsonPath("$.balance").value(100.00));
        getAs(admin.token(), HISTORY, iban)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"));
        // Section 2.1: an admin does not open accounts and does not move money
        postAs(admin.token(), "/accounts", null).andExpect(problem(403, "FORBIDDEN"));
        deposit(admin, iban, "10.00").andExpect(problem(403, "FORBIDDEN"));
        withdraw(admin, iban, "10.00").andExpect(problem(403, "FORBIDDEN"));

        assertThat(balance(customer, iban)).isEqualByComparingTo("100.00");
    }

    @Test
    void onlyAnAdminBlocksAndUnblocksAnAccount() throws Exception {
        Customer admin = newAdmin();
        Customer customer = newCustomer();
        String iban = fundedAccount(customer, "100.00");

        setStatus(customer, iban, "BLOCKED").andExpect(problem(403, "FORBIDDEN"));
        setStatus(admin, iban, "FROZEN").andExpect(problem(400, "VALIDATION_ERROR"));
        setStatus(admin, UNKNOWN_IBAN, "BLOCKED").andExpect(problem(404, "NOT_FOUND"));
        // Still active after the refused requests
        withdraw(customer, iban, "10.00").andExpect(status().isOk());

        setStatus(admin, iban, "BLOCKED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(iban))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.balance").value(90.00));
        // BR-07: no cash operation on a blocked account, which keeps its balance and history
        deposit(customer, iban, "10.00").andExpect(problem(422, "ACCOUNT_BLOCKED"));
        withdraw(customer, iban, "10.00").andExpect(problem(422, "ACCOUNT_BLOCKED"));
        getAs(customer.token(), "/accounts/{iban}", iban)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.balance").value(90.00));
        assertThat(ledger(customer, iban)).hasSize(2);

        setStatus(admin, iban, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        withdraw(customer, iban, "10.00").andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(80.00));
    }

    @Test
    void unknownAccount() throws Exception {
        Customer customer = newCustomer();

        getAs(customer.token(), "/accounts/{iban}", UNKNOWN_IBAN).andExpect(problem(404, "NOT_FOUND"));
        getAs(customer.token(), HISTORY, UNKNOWN_IBAN).andExpect(problem(404, "NOT_FOUND"));
        deposit(customer, UNKNOWN_IBAN, "10.00").andExpect(problem(404, "NOT_FOUND"));
        withdraw(customer, UNKNOWN_IBAN, "10.00").andExpect(problem(404, "NOT_FOUND"));
    }

    @Test
    void depositAndWithdraw() throws Exception {
        Customer customer = newCustomer();
        String iban = openAccount(customer);

        deposit(customer, iban, "200.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(iban))
                .andExpect(jsonPath("$.balance").value(200.00));
        withdraw(customer, iban, "49.99")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(iban))
                .andExpect(jsonPath("$.balance").value(150.01));

        assertThat(balance(customer, iban)).isEqualByComparingTo("150.01");
        // One line per operation, newest first, each with the balance it produced
        getAs(customer.token(), HISTORY, iban)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").isNotEmpty())
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAW"))
                .andExpect(jsonPath("$.content[0].amount").value(49.99))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(150.01))
                .andExpect(jsonPath("$.content[0].description").value("Cash withdrawal"))
                .andExpect(jsonPath("$.content[0].transferId").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[1].amount").value(200.00))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(200.00))
                .andExpect(jsonPath("$.content[1].description").value("Cash deposit"));
        assertLedgerMatchesBalance(customer, iban);
    }

    @Test
    void withdrawAboveTheBalance() throws Exception {
        Customer customer = newCustomer();
        String iban = fundedAccount(customer, "100.00");

        withdraw(customer, iban, "100.01").andExpect(problem(422, "INSUFFICIENT_FUNDS"));

        assertThat(balance(customer, iban)).isEqualByComparingTo("100.00");
        assertThat(ledger(customer, iban)).hasSize(1);
        // The whole balance can be withdrawn: it reaches zero, never below
        withdraw(customer, iban, "100.00").andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0.00));
    }

    @Test
    void invalidAmount() throws Exception {
        Customer customer = newCustomer();
        String iban = fundedAccount(customer, "100.00");

        // BR-01: greater than zero, at most 2 decimal places
        for (String amount : List.of("0", "-5.00", "1.001")) {
            deposit(customer, iban, amount).andExpect(problem(400, "VALIDATION_ERROR"));
            withdraw(customer, iban, amount).andExpect(problem(400, "VALIDATION_ERROR"));
        }
        postAs(customer.token(), "/accounts/{iban}/deposit", "{}", iban).andExpect(problem(400, "VALIDATION_ERROR"));

        assertThat(balance(customer, iban)).isEqualByComparingTo("100.00");
        assertThat(ledger(customer, iban)).hasSize(1);
    }

    @Test
    void historyPagination() throws Exception {
        Customer customer = newCustomer();
        String iban = openAccount(customer);
        // Five operations: balance after each one is 10, 30, 60, 100, 150
        for (String amount : List.of("10.00", "20.00", "30.00", "40.00", "50.00")) {
            deposit(customer, iban, amount).andExpect(status().isOk());
        }

        getAs(customer.token(), HISTORY + "?size=2", iban)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(150.00))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(100.00));
        getAs(customer.token(), HISTORY + "?page=1&size=2", iban)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(60.00))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(30.00));
        getAs(customer.token(), HISTORY + "?page=2&size=2", iban)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(10.00));
        // Default page size 20: everything on one page, newest first
        ResultActions everything = getAs(customer.token(), HISTORY, iban)
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
        List<String> createdAt = read(everything, "$.content[*].createdAt");
        assertThat(createdAt.stream().map(Instant::parse)).hasSize(5).isSortedAccordingTo(Comparator.reverseOrder());

        getAs(customer.token(), HISTORY + "?page=-1", iban).andExpect(problem(400, "VALIDATION_ERROR"));
        getAs(customer.token(), HISTORY + "?size=0", iban).andExpect(problem(400, "VALIDATION_ERROR"));
        getAs(customer.token(), HISTORY + "?size=101", iban).andExpect(problem(400, "VALIDATION_ERROR"));
    }

    @Test
    void historyDateFilter() throws Exception {
        Customer customer = newCustomer();
        String iban = fundedAccount(customer, "100.00");
        withdraw(customer, iban, "30.00").andExpect(status().isOk());
        // The service reads the dates as days in Europe/Rome
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Rome"));
        LocalDate yesterday = today.minusDays(1);
        String range = HISTORY + "?from={from}&to={to}";

        // Both bounds are inclusive
        getAs(customer.token(), range, iban, today, today)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAW"))
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"));
        getAs(customer.token(), HISTORY + "?from={from}", iban, today)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs(customer.token(), HISTORY + "?to={to}", iban, today)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        // A range entirely in the past, or in the future, has no lines
        getAs(customer.token(), range, iban, today.minusDays(30), yesterday)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
        getAs(customer.token(), HISTORY + "?to={to}", iban, yesterday)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        getAs(customer.token(), HISTORY + "?from={from}", iban, today.plusDays(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        getAs(customer.token(), range, iban, today, yesterday).andExpect(problem(400, "VALIDATION_ERROR"));
        getAs(customer.token(), HISTORY + "?from=25-11-2026", iban).andExpect(problem(400, "VALIDATION_ERROR"));
    }

    @Test
    void concurrentDepositsAndWithdrawals() throws Exception {
        Customer customer = newCustomer();
        String iban = fundedAccount(customer, "100.00");
        // Even if every withdrawal ran first, the balance would only reach zero
        List<Callable<ResultActions>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> deposit(customer, iban, "5.00"));
            calls.add(() -> withdraw(customer, iban, "5.00"));
        }

        List<ResultActions> responses = inParallel(calls);

        assertThat(statuses(responses)).hasSize(40).containsOnly(200);
        assertThat(balance(customer, iban)).isEqualByComparingTo("100.00");
        // FR-TRX-02: exactly one line per operation, and the ledger adds up to the balance
        assertThat(ledger(customer, iban)).hasSize(41);
        assertLedgerMatchesBalance(customer, iban);
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        Customer customer = newCustomer();
        String iban = fundedAccount(customer, "50.00");

        // 20 x 5.00 requested, only 50.00 available
        List<ResultActions> responses = inParallel(
                Collections.nCopies(20, () -> withdraw(customer, iban, "5.00")));

        // BR-02: ten withdrawals empty the account, the other ten are refused
        List<Integer> statuses = statuses(responses);
        assertThat(statuses).hasSize(20).containsOnly(200, 422);
        assertThat(Collections.frequency(statuses, 200)).isEqualTo(10);
        for (ResultActions refused : withStatus(responses, 422)) {
            refused.andExpect(problem(422, "INSUFFICIENT_FUNDS"));
        }
        assertThat(balance(customer, iban)).isEqualByComparingTo("0.00");
        List<Map<String, Object>> lines = ledger(customer, iban);
        assertThat(lines).hasSize(11).allSatisfy(
                line -> assertThat(money(line.get("balanceAfter"))).isNotNegative());
        assertLedgerMatchesBalance(customer, iban);
    }

    private ResultActions withdraw(Customer owner, String iban, String amount) throws Exception {
        return postAs(owner.token(), "/accounts/{iban}/withdraw", """
                {"amount":%s,"description":"Cash withdrawal"}""".formatted(amount), iban);
    }
}
