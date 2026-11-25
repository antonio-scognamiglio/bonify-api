package com.bonifybank.bonify.modules.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.account.dto.AccountResponse;
import com.bonifybank.bonify.modules.account.dto.CashOperationRequest;
import com.bonifybank.bonify.modules.account.dto.UpdateAccountStatusRequest;
import com.bonifybank.bonify.modules.customer.CurrentCustomerResolver;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.TransactionEntity;
import com.bonifybank.bonify.modules.transaction.TransactionRepository;
import com.bonifybank.bonify.modules.transaction.TransactionType;

/** FR-ACC-01..06 / FR-TRX-02: account operations; every balance change records exactly one ledger line. */
class AccountServiceTests {

    private static final String IBAN = "IT60X0542811101000000000001";
    private static final String UNKNOWN_IBAN = "IT60X0542811101000000999999";

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CurrentCustomerResolver currentCustomerResolver = mock(CurrentCustomerResolver.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);

    private AccountService accountService;
    private CustomerEntity owner;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, currentCustomerResolver, transactionRepository);
        owner = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        account = AccountEntity.builder()
                .id(1L)
                .iban(IBAN)
                .balance(new BigDecimal("100.00"))
                .currency(Currency.EUR)
                .status(AccountStatus.ACTIVE)
                .customer(owner)
                .build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(owner);
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.of(account));
        when(accountRepository.findByIbanForUpdate(IBAN)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // FR-ACC-01
    @Test
    void openAccountCreatesActiveEmptyEurAccountOwnedByCaller() {
        AccountResponse response = accountService.openAccount();

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).save(captor.capture());
        AccountEntity saved = captor.getValue();
        assertThat(saved.getCustomer()).isSameAs(owner);
        assertThat(saved.getBalance()).isEqualByComparingTo("0.00");
        assertThat(saved.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getIban()).hasSize(27).startsWith("IT60X0542811101000000");
        assertThat(response.iban()).isEqualTo(saved.getIban());
        assertThat(response.customerId()).isEqualTo(owner.getId());
    }

    @Test
    void openAccountGeneratesANewIbanWhenTheCandidateIsTaken() {
        when(accountRepository.existsByIban(anyString())).thenReturn(true, false);

        AccountResponse response = accountService.openAccount();

        // One check per candidate: the first was taken, the second was free and is the one saved
        ArgumentCaptor<String> candidates = ArgumentCaptor.forClass(String.class);
        verify(accountRepository, times(2)).existsByIban(candidates.capture());
        assertThat(response.iban()).isEqualTo(candidates.getAllValues().get(1));
    }

    // FR-ACC-02
    @Test
    void listMyAccountsReturnsOnlyTheCallerAccounts() {
        when(accountRepository.findByCustomer_Id(owner.getId())).thenReturn(List.of(account));

        List<AccountResponse> result = accountService.listMyAccounts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).iban()).isEqualTo(IBAN);
        assertThat(result.get(0).balance()).isEqualByComparingTo("100.00");
        verify(accountRepository).findByCustomer_Id(owner.getId());
    }

    // FR-ACC-03
    @Test
    void ownerCanViewAccount() {
        AccountResponse response = accountService.getAccount(IBAN);

        assertThat(response.iban()).isEqualTo(IBAN);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.currency()).isEqualTo("EUR");
    }

    @Test
    void adminCanViewAnyAccount() {
        CustomerEntity admin = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.ADMIN).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(admin);

        assertThat(accountService.getAccount(IBAN).iban()).isEqualTo(IBAN);
    }

    @Test
    void anotherCustomerCannotViewAccount() {
        actAsAnotherCustomer();

        assertCode(() -> accountService.getAccount(IBAN), ErrorCodes.FORBIDDEN);
    }

    @Test
    void viewingUnknownAccountIsNotFound() {
        assertCode(() -> accountService.getAccount(UNKNOWN_IBAN), ErrorCodes.NOT_FOUND);
    }

    // FR-ACC-04 / FR-ACC-05
    @Test
    void depositRecordsDepositLine() {
        accountService.deposit(IBAN, new CashOperationRequest(new BigDecimal("50.00"), " salary "));

        TransactionEntity line = savedLine();
        assertThat(line.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(line.getAmount()).isEqualByComparingTo("50.00");
        assertThat(line.getBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(line.getDescription()).isEqualTo("salary");
        assertThat(line.getAccount()).isSameAs(account);
        assertThat(line.getTransfer()).isNull();
    }

    @Test
    void withdrawRecordsWithdrawLine() {
        accountService.withdraw(IBAN, new CashOperationRequest(new BigDecimal("30.00"), null));

        TransactionEntity line = savedLine();
        assertThat(line.getTransactionType()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(line.getAmount()).isEqualByComparingTo("30.00");
        assertThat(line.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(line.getDescription()).isNull();
    }

    @Test
    void withdrawOfTheWholeBalanceLeavesZero() {
        AccountResponse response = accountService.withdraw(IBAN, new CashOperationRequest(new BigDecimal("100.00"), null));

        assertThat(response.balance()).isEqualByComparingTo("0.00");
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(savedLine().getBalanceAfter()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectedWithdrawRecordsNothing() {
        assertCode(() -> accountService.withdraw(IBAN, new CashOperationRequest(new BigDecimal("500.00"), null)),
                ErrorCodes.INSUFFICIENT_FUNDS);

        verify(transactionRepository, never()).save(any());
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void depositOnBlockedAccountRecordsNothing() {
        account.setStatus(AccountStatus.BLOCKED);

        assertCode(() -> accountService.deposit(IBAN, new CashOperationRequest(BigDecimal.ONE, null)),
                ErrorCodes.ACCOUNT_BLOCKED);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdrawOnBlockedAccountIsRejected() {
        account.setStatus(AccountStatus.BLOCKED);

        assertCode(() -> accountService.withdraw(IBAN, new CashOperationRequest(BigDecimal.ONE, null)),
                ErrorCodes.ACCOUNT_BLOCKED);

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void depositByANonOwnerIsForbiddenAndSavesNothing() {
        actAsAnotherCustomer();

        assertCode(() -> accountService.deposit(IBAN, new CashOperationRequest(BigDecimal.TEN, null)),
                ErrorCodes.FORBIDDEN);

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawByANonOwnerIsForbiddenAndSavesNothing() {
        actAsAnotherCustomer();

        assertCode(() -> accountService.withdraw(IBAN, new CashOperationRequest(BigDecimal.TEN, null)),
                ErrorCodes.FORBIDDEN);

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void depositOnUnknownAccountIsNotFound() {
        assertCode(() -> accountService.deposit(UNKNOWN_IBAN, new CashOperationRequest(BigDecimal.TEN, null)),
                ErrorCodes.NOT_FOUND);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdrawOnUnknownAccountIsNotFound() {
        assertCode(() -> accountService.withdraw(UNKNOWN_IBAN, new CashOperationRequest(BigDecimal.TEN, null)),
                ErrorCodes.NOT_FOUND);

        verify(transactionRepository, never()).save(any());
    }

    // FR-ACC-06
    @ParameterizedTest
    @CsvSource({ "BLOCKED, BLOCKED", "ACTIVE, ACTIVE", "blocked, BLOCKED", "active, ACTIVE", "'  Blocked ', BLOCKED" })
    void updateStatusAcceptsValidValuesIgnoringCaseAndSpaces(String input, AccountStatus expected) {
        account.setStatus(expected == AccountStatus.BLOCKED ? AccountStatus.ACTIVE : AccountStatus.BLOCKED);

        AccountResponse response = accountService.updateStatus(IBAN, new UpdateAccountStatusRequest(input));

        assertThat(account.getStatus()).isEqualTo(expected);
        assertThat(response.status()).isEqualTo(expected.name());
    }

    @ParameterizedTest
    @ValueSource(strings = { "CLOSED", "ACTIVE BLOCKED", "1" })
    void updateStatusRejectsInvalidValueWithoutLeakingInternals(String input) {
        assertThatThrownBy(() -> accountService.updateStatus(IBAN, new UpdateAccountStatusRequest(input)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
                    assertThat(e.getMessage()).isEqualTo("status must be ACTIVE or BLOCKED");
                });

        verify(accountRepository, never()).save(any());
    }

    @Test
    void updateStatusOnUnknownAccountIsNotFound() {
        assertCode(() -> accountService.updateStatus(UNKNOWN_IBAN, new UpdateAccountStatusRequest("BLOCKED")),
                ErrorCodes.NOT_FOUND);
    }

    private void actAsAnotherCustomer() {
        CustomerEntity other = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(other);
    }

    private static void assertCode(ThrowingCallable call, ErrorCodes code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(code);
    }

    private TransactionEntity savedLine() {
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
