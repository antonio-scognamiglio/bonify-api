package com.bonifybank.bonify.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.jayway.jsonpath.JsonPath;

/**
 * Shared setup for the end-to-end tests: full Spring context, real PostgreSQL and MockMvc through
 * the real security filter chain, with tokens obtained from the login endpoint.
 * <p>
 * The database may contain other data (locally it is the developer's one): every test creates its
 * own customers and accounts with random emails and tax codes, never relies on global counts, and
 * each class deletes what it created, and only that, when it ends.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "bonify.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTestSupport {

    static final String API = "/api/v1";
    static final String PASSWORD = "Str0ng-password";
    static final String UNKNOWN_IBAN = "IT00Z0000000000000000000000";

    private static final String EMAIL_DOMAIN = "@integration-tests.bonify.example";
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final long PARALLEL_TIMEOUT_SECONDS = 60;

    // Children first (foreign keys), always scoped to one customer created by this class
    private static final List<String> CLEANUP_STATEMENTS = List.of(
            "delete from account_transactions where account_id in (select id from accounts where customer_id = ?)",
            "delete from transfer where from_account_id in (select id from accounts where customer_id = ?)",
            "delete from transfer where to_account_id in (select id from accounts where customer_id = ?)",
            "delete from refresh_tokens where customer_id = ?",
            "delete from accounts where customer_id = ?",
            "delete from customers where id = ?");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    // Cleanup registry: the customers created by this class and every email it tried to register
    private final Set<UUID> createdCustomerIds = ConcurrentHashMap.newKeySet();
    private final Set<String> usedEmails = ConcurrentHashMap.newKeySet();

    /** A logged-in customer (or admin) with the token pair returned by the login. */
    record Customer(UUID id, String email, String token, String refreshToken) {
    }

    @AfterAll
    void deleteCreatedData() {
        // By email too: it also finds a customer created by a registration that was expected to fail
        for (String email : usedEmails) {
            createdCustomerIds.addAll(jdbc.queryForList("select id from customers where email = ?", UUID.class,
                    email.toLowerCase(Locale.ROOT)));
        }
        for (String statement : CLEANUP_STATEMENTS) {
            for (UUID customerId : createdCustomerIds) {
                jdbc.update(statement, customerId);
            }
        }
    }

    // ---- Test data -------------------------------------------------------------------------------

    /** A unique lower-case email, remembered for the cleanup. */
    String newEmail() {
        String email = "it-" + UUID.randomUUID() + EMAIL_DOMAIN;
        usedEmails.add(email);
        return email;
    }

    /** A random upper-case tax code with the format required by the registration (FR-AUTH-01). */
    static String newTaxCode() {
        return pick(LETTERS, 6) + pick(DIGITS, 2) + pick("ABCDEHLMPRST", 1) + pick(DIGITS, 2)
                + pick(LETTERS, 1) + pick(DIGITS, 3) + pick(LETTERS, 1);
    }

    private static String pick(String alphabet, int count) {
        StringBuilder picked = new StringBuilder();
        for (int i = 0; i < count; i++) {
            picked.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return picked.toString();
    }

    // ---- HTTP ------------------------------------------------------------------------------------

    static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Sends a request under /api/v1; no Authorization header when the token is null. */
    ResultActions send(HttpMethod method, String token, String body, String path, Object... uriVariables)
            throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, API + path, uriVariables);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    ResultActions getAs(String token, String path, Object... uriVariables) throws Exception {
        return send(HttpMethod.GET, token, null, path, uriVariables);
    }

    ResultActions postAs(String token, String path, String body, Object... uriVariables) throws Exception {
        return send(HttpMethod.POST, token, body, path, uriVariables);
    }

    ResultActions patchAs(String token, String path, String body, Object... uriVariables) throws Exception {
        return send(HttpMethod.PATCH, token, body, path, uriVariables);
    }

    /** Reads a value from the JSON body of a response. */
    static <T> T read(ResultActions response, String jsonPath) throws Exception {
        return JsonPath.read(response.andReturn().getResponse().getContentAsString(), jsonPath);
    }

    /** An RFC 7807 error response with the given status and code (section 8). */
    static ResultMatcher problem(int status, String code) {
        List<ResultMatcher> matchers = List.of(
                status().is(status),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.status").value(status),
                jsonPath("$.code").value(code),
                jsonPath("$.type").isNotEmpty(),
                jsonPath("$.title").isNotEmpty());
        return result -> {
            for (ResultMatcher matcher : matchers) {
                matcher.match(result);
            }
        };
    }

    // ---- Customers and sessions ------------------------------------------------------------------

    ResultActions register(String email, String taxCode) throws Exception {
        return postAs(null, "/auth/register", """
                {"firstName":"Mario","lastName":"Rossi","email":"%s","phoneNumber":"+39 333 1234567",
                 "taxCode":"%s","password":"%s"}""".formatted(email, taxCode, PASSWORD));
    }

    ResultActions login(String email, String password) throws Exception {
        return postAs(null, "/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    ResultActions refresh(String refreshToken) throws Exception {
        return postAs(null, "/auth/refresh", """
                {"refreshToken":"%s"}""".formatted(refreshToken));
    }

    /** Registers a new customer over HTTP and logs in. */
    Customer newCustomer() throws Exception {
        String email = newEmail();
        String id = read(register(email, newTaxCode()).andExpect(status().isCreated()), "$.id");
        createdCustomerIds.add(UUID.fromString(id));
        return signIn(UUID.fromString(id), email);
    }

    /** The admin is created directly in the database (section 2), then logs in over HTTP like anyone else. */
    Customer newAdmin() throws Exception {
        String email = newEmail();
        CustomerEntity admin = customerRepository.save(CustomerEntity.builder()
                .firstName("Ada")
                .lastName("Admin")
                .email(email)
                .phoneNumber("+39 02 1234567")
                .taxCode(newTaxCode())
                .password(passwordEncoder.encode(PASSWORD))
                .role(CustomerRole.ADMIN)
                .build());
        createdCustomerIds.add(admin.getId());
        return signIn(admin.getId(), email);
    }

    private Customer signIn(UUID id, String email) throws Exception {
        ResultActions response = login(email, PASSWORD).andExpect(status().isOk());
        return new Customer(id, email, read(response, "$.accessToken"), read(response, "$.refreshToken"));
    }

    // ---- Accounts and ledger ---------------------------------------------------------------------

    /** Opens an account and returns its IBAN. */
    String openAccount(Customer owner) throws Exception {
        return read(postAs(owner.token(), "/accounts", null).andExpect(status().isCreated()), "$.iban");
    }

    ResultActions deposit(Customer owner, String iban, String amount) throws Exception {
        return postAs(owner.token(), "/accounts/{iban}/deposit", """
                {"amount":%s,"description":"Cash deposit"}""".formatted(amount), iban);
    }

    /** Opens an account with the given starting balance and returns its IBAN. */
    String fundedAccount(Customer owner, String amount) throws Exception {
        String iban = openAccount(owner);
        deposit(owner, iban, amount).andExpect(status().isOk());
        return iban;
    }

    /** FR-ACC-06: PATCH the account status (BLOCKED / ACTIVE), an admin-only operation. */
    ResultActions setStatus(Customer caller, String iban, String status) throws Exception {
        return patchAs(caller.token(), "/accounts/{iban}/status", """
                {"status":"%s"}""".formatted(status), iban);
    }

    BigDecimal balance(Customer viewer, String iban) throws Exception {
        return money(read(getAs(viewer.token(), "/accounts/{iban}", iban).andExpect(status().isOk()), "$.balance"));
    }

    /** The whole history of the account, newest first, read through every page of the endpoint. */
    List<Map<String, Object>> ledger(Customer viewer, String iban) throws Exception {
        List<Map<String, Object>> lines = new ArrayList<>();
        boolean last = false;
        for (int page = 0; !last; page++) {
            ResultActions response = getAs(viewer.token(), "/accounts/{iban}/transactions?page={page}&size=100",
                    iban, page).andExpect(status().isOk());
            lines.addAll(read(response, "$.content"));
            last = read(response, "$.last");
        }
        return lines;
    }

    /** FR-TRX-02: the signed sum of the lines is the balance, and so is the balance after the latest line. */
    void assertLedgerMatchesBalance(Customer owner, String iban) throws Exception {
        List<Map<String, Object>> lines = ledger(owner, iban);
        BigDecimal balance = balance(owner, iban);
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            BigDecimal amount = money(line.get("amount"));
            sum = switch ((String) line.get("type")) {
                case "DEPOSIT", "TRANSFER_IN" -> sum.add(amount);
                case "WITHDRAW", "TRANSFER_OUT" -> sum.subtract(amount);
                default -> throw new IllegalStateException("Unknown ledger line type: " + line.get("type"));
            };
        }
        assertThat(sum).isEqualByComparingTo(balance);
        assertThat(money(lines.getFirst().get("balanceAfter"))).isEqualByComparingTo(balance);
    }

    /** Amounts are JSON numbers: compared as BigDecimal, never as floating point. */
    static BigDecimal money(Object jsonNumber) {
        return new BigDecimal(jsonNumber.toString());
    }

    // ---- Concurrency -----------------------------------------------------------------------------

    /**
     * Runs every call on its own thread, all released together, and returns the responses in the
     * order of the calls. A call that does not finish in time (deadlock, stuck request) fails the test.
     */
    List<ResultActions> inParallel(List<Callable<ResultActions>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResultActions>> pending = new ArrayList<>();
            for (Callable<ResultActions> call : calls) {
                pending.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return call.call();
                }));
            }
            assertThat(ready.await(PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PARALLEL_TIMEOUT_SECONDS);
            List<ResultActions> responses = new ArrayList<>();
            for (Future<ResultActions> response : pending) {
                responses.add(response.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    static List<Integer> statuses(List<ResultActions> responses) {
        return responses.stream().map(response -> response.andReturn().getResponse().getStatus()).toList();
    }

    static List<ResultActions> withStatus(List<ResultActions> responses, int status) {
        return responses.stream()
                .filter(response -> response.andReturn().getResponse().getStatus() == status)
                .toList();
    }
}
