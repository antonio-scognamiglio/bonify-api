package com.bonifybank.bonify.seed;

import com.bonifybank.bonify.modules.account.AccountEntity;
import com.bonifybank.bonify.modules.account.AccountRepository;
import com.bonifybank.bonify.modules.account.AccountStatus;
import com.bonifybank.bonify.modules.account.Currency;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.TransactionEntity;
import com.bonifybank.bonify.modules.transaction.TransactionRepository;
import com.bonifybank.bonify.modules.transaction.TransactionType;
import com.bonifybank.bonify.modules.transfer.Status;
import com.bonifybank.bonify.modules.transfer.TransferEntity;
import com.bonifybank.bonify.modules.transfer.TransferRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Demo data loaded at startup: one admin and five customers with accounts, cash operations and
 * transfers spread over the last three months.
 * <p>
 * It runs only when {@code bonify.seed.enabled=true} and only on a database without the seed admin,
 * so restarting the application never duplicates the data. Every balance change goes through the
 * same rules as the services (no overdraft, one ledger line per change, two lines per transfer),
 * then the timestamps are moved into the past so the history looks real.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "bonify.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    /** Shared by every seeded user, admin included. */
    public static final String SEED_PASSWORD = "Bonify2026!";
    public static final String ADMIN_EMAIL = "admin@bonify.example";

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    // Same 21-character prefix used by AccountService, so the seeded IBANs look like the generated ones
    private static final String IBAN_PREFIX = "IT60X0542811101000000";

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransferRepository transferRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    // Rows whose creation timestamp is moved into the past once everything has been inserted
    private final List<Backdate> backdates = new ArrayList<>();

    private record Backdate(String sql, Object id, Instant at) {
    }

    public DataSeeder(CustomerRepository customerRepository, AccountRepository accountRepository,
            TransactionRepository transactionRepository, TransferRepository transferRepository,
            PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transferRepository = transferRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (customerRepository.existsByEmail(ADMIN_EMAIL)) {
            log.info("Seed data already present, skipping");
            return;
        }
        backdates.clear();
        // One hash for everyone: BCrypt is slow on purpose and the password is the same
        String passwordHash = passwordEncoder.encode(SEED_PASSWORD);

        // ---- Customers -------------------------------------------------------------------------
        customer("Admin", "Bonify", ADMIN_EMAIL, "+39 06 1234567", "BNFDMN80A01H501X",
                CustomerRole.ADMIN, passwordHash, 120);
        CustomerEntity mario = customer("Mario", "Rossi", "mario.rossi@bonify.example", "+39 333 1234567",
                "RSSMRA85M01H501Q", CustomerRole.CUSTOMER, passwordHash, 95);
        CustomerEntity giulia = customer("Giulia", "Bianchi", "giulia.bianchi@bonify.example", "+39 340 2345678",
                "BNCGLI90A41F205X", CustomerRole.CUSTOMER, passwordHash, 94);
        CustomerEntity luca = customer("Luca", "Verdi", "luca.verdi@bonify.example", "+39 347 3456789",
                "VRDLCU78C15L219K", CustomerRole.CUSTOMER, passwordHash, 93);
        CustomerEntity sofia = customer("Sofia", "Esposito", "sofia.esposito@bonify.example", "+39 328 4567890",
                "SPSSFO95E52F839T", CustomerRole.CUSTOMER, passwordHash, 92);
        CustomerEntity alessandro = customer("Alessandro", "Romano", "alessandro.romano@bonify.example",
                "+39 389 5678901", "RMNLSN88H10A662Z", CustomerRole.CUSTOMER, passwordHash, 91);

        // ---- Accounts ----------------------------------------------------------------------------
        AccountEntity marioMain = account(mario, "100001", 91);
        AccountEntity marioSavings = account(mario, "100002", 86);
        AccountEntity giuliaMain = account(giulia, "100003", 90);
        AccountEntity lucaMain = account(luca, "100004", 89);
        AccountEntity lucaSecond = account(luca, "100005", 89);
        AccountEntity sofiaMain = account(sofia, "100006", 88);
        AccountEntity alessandroMain = account(alessandro, "100007", 87);

        // ---- Movements, in chronological order ---------------------------------------------------
        deposit(marioMain, "2500.00", "Stipendio", 90, 9);
        deposit(giuliaMain, "4200.00", "Accredito stipendio", 89, 10);
        deposit(lucaMain, "1800.00", "Versamento contanti", 88, 11);
        deposit(lucaSecond, "500.00", "Versamento iniziale", 88, 12);
        deposit(sofiaMain, "3100.00", "Stipendio", 87, 9);
        deposit(alessandroMain, "600.00", "Versamento contanti", 86, 16);
        transfer(marioMain, marioSavings, "1000.00", "Accantonamento risparmi", 85, 18);
        withdraw(marioMain, "200.00", "Prelievo bancomat", 80, 13);
        transfer(lucaMain, giuliaMain, "350.00", "Quota affitto", 78, 8);
        transfer(sofiaMain, alessandroMain, "120.00", "Cena di compleanno", 75, 22);
        withdraw(lucaMain, "150.00", "Prelievo ATM", 70, 19);
        transfer(lucaMain, lucaSecond, "100.00", "Giroconto", 65, 10);
        deposit(marioMain, "2500.00", "Stipendio", 60, 9);
        deposit(giuliaMain, "4200.00", "Accredito stipendio", 60, 10);
        deposit(lucaMain, "1800.00", "Stipendio", 60, 11);
        deposit(sofiaMain, "3100.00", "Stipendio", 59, 9);
        withdraw(giuliaMain, "800.00", "Prelievo contanti", 58, 17);
        transfer(marioMain, giuliaMain, "75.50", "Biglietti concerto", 55, 21);
        transfer(alessandroMain, marioMain, "45.00", "Rimborso spesa", 50, 20);
        withdraw(sofiaMain, "300.00", "Prelievo bancomat", 45, 12);
        transfer(giuliaMain, sofiaMain, "1200.00", "Acconto vacanza", 40, 15);
        deposit(alessandroMain, "250.00", "Versamento contanti", 35, 10);
        deposit(marioMain, "2500.00", "Stipendio", 30, 9);
        deposit(giuliaMain, "4200.00", "Accredito stipendio", 30, 10);
        deposit(lucaMain, "1800.00", "Stipendio", 30, 11);
        deposit(sofiaMain, "3100.00", "Stipendio", 29, 9);
        transfer(marioMain, marioSavings, "1000.00", "Accantonamento risparmi", 28, 18);
        transfer(lucaMain, giuliaMain, "350.00", "Quota affitto", 25, 8);
        withdraw(marioMain, "400.00", "Prelievo bancomat", 20, 13);
        transfer(sofiaMain, lucaMain, "60.00", "Regalo di compleanno", 18, 19);
        transfer(marioSavings, marioMain, "300.00", "Giroconto", 15, 10);
        withdraw(alessandroMain, "150.00", "Prelievo ATM", 12, 18);
        transfer(giuliaMain, alessandroMain, "500.00", "Prestito", 10, 14);
        withdraw(lucaMain, "200.00", "Prelievo contanti", 7, 17);
        transfer(marioMain, sofiaMain, "89.90", "Quota palestra", 5, 8);
        deposit(alessandroMain, "100.00", "Versamento contanti", 3, 11);
        transfer(alessandroMain, giuliaMain, "50.00", "Restituzione prestito (1/10)", 2, 20);
        withdraw(sofiaMain, "50.00", "Prelievo bancomat", 1, 12);

        // A blocked account, so the admin flows have something to show
        lucaSecond.setStatus(AccountStatus.BLOCKED);

        // Inserts first, then the timestamps: @CreationTimestamp would overwrite any value set earlier
        customerRepository.flush();
        accountRepository.flush();
        transferRepository.flush();
        transactionRepository.flush();
        for (Backdate backdate : backdates) {
            jdbc.update(backdate.sql(), Timestamp.from(backdate.at()), backdate.id());
        }

        log.info("Seed data created: 1 admin and 5 customers (password for all: {}), admin email {}",
                SEED_PASSWORD, ADMIN_EMAIL);
    }

    // ---- Builders --------------------------------------------------------------------------------

    private CustomerEntity customer(String firstName, String lastName, String email, String phone, String taxCode,
            CustomerRole role, String passwordHash, int daysAgo) {
        CustomerEntity customer = customerRepository.save(CustomerEntity.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phoneNumber(phone)
                .taxCode(taxCode)
                .password(passwordHash)
                .role(role)
                .build());
        Instant at = at(daysAgo, 9);
        backdates.add(new Backdate("update customers set created_at = ?, updated_at = created_at where id = ?",
                customer.getId(), at));
        return customer;
    }

    private AccountEntity account(CustomerEntity owner, String suffix, int daysAgo) {
        AccountEntity account = accountRepository.save(AccountEntity.builder()
                .iban(IBAN_PREFIX + suffix)
                .balance(new BigDecimal("0.00"))
                .currency(Currency.EUR)
                .status(AccountStatus.ACTIVE)
                .customer(owner)
                .build());
        backdates.add(new Backdate("update accounts set created_at = ? where id = ?", account.getId(),
                at(daysAgo, 8)));
        return account;
    }

    // ---- Movements -------------------------------------------------------------------------------

    private void deposit(AccountEntity account, String amount, String description, int daysAgo, int hour) {
        BigDecimal value = new BigDecimal(amount);
        account.setBalance(account.getBalance().add(value));
        line(account, TransactionType.DEPOSIT, value, null, description, at(daysAgo, hour));
    }

    private void withdraw(AccountEntity account, String amount, String description, int daysAgo, int hour) {
        BigDecimal value = new BigDecimal(amount);
        account.setBalance(debit(account, value));
        line(account, TransactionType.WITHDRAW, value, null, description, at(daysAgo, hour));
    }

    private void transfer(AccountEntity from, AccountEntity to, String amount, String description, int daysAgo,
            int hour) {
        BigDecimal value = new BigDecimal(amount);
        Instant at = at(daysAgo, hour);
        from.setBalance(debit(from, value));
        to.setBalance(to.getBalance().add(value));

        TransferEntity transfer = transferRepository.save(TransferEntity.builder()
                .amount(value)
                .description(description)
                .idempotencyKey(UUID.randomUUID().toString())
                .request(fingerprint(from.getIban(), to.getIban(), value, description))
                .status(Status.COMPLETED)
                .fromAccount(from)
                .toAccount(to)
                .build());
        backdates.add(new Backdate("update transfer set created_at = ? where id = ?", transfer.getId(), at));

        line(from, TransactionType.TRANSFER_OUT, value, transfer, description, at);
        line(to, TransactionType.TRANSFER_IN, value, transfer, description, at);
    }

    // BR-02: the seed must respect the same no-overdraft rule as the services
    private static BigDecimal debit(AccountEntity account, BigDecimal amount) {
        BigDecimal result = account.getBalance().subtract(amount);
        if (result.signum() < 0) {
            throw new IllegalStateException("Seed data would overdraw account " + account.getIban());
        }
        return result;
    }

    private void line(AccountEntity account, TransactionType type, BigDecimal amount, TransferEntity transfer,
            String description, Instant at) {
        TransactionEntity line = transactionRepository.save(TransactionEntity.builder()
                .account(account)
                .transactionType(type)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .transfer(transfer)
                .description(description)
                .build());
        backdates.add(new Backdate("update account_transactions set created_at = ? where id = ?", line.getId(), at));
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private static Instant at(int daysAgo, int hour) {
        // Deterministic minutes, so two movements of the same day keep a stable order
        int minute = (daysAgo * 7 + hour * 13) % 60;
        return LocalDate.now(ZONE).minusDays(daysAgo).atTime(hour, minute).atZone(ZONE).toInstant();
    }

    // Same format as TransferService, so a seeded transfer is indistinguishable from a real one
    private static String fingerprint(String sourceIban, String targetIban, BigDecimal amount, String description) {
        String raw = sourceIban + "|" + targetIban + "|" + amount.toPlainString() + "|"
                + (description != null ? description.trim() : "");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
