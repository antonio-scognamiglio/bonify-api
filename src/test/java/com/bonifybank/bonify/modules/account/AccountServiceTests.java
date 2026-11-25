package com.bonifybank.bonify.modules.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.account.dto.CashOperationRequest;
import com.bonifybank.bonify.modules.customer.CurrentCustomerResolver;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.TransactionEntity;
import com.bonifybank.bonify.modules.transaction.TransactionRepository;
import com.bonifybank.bonify.modules.transaction.TransactionType;

/** FR-ACC-04 / FR-ACC-05 / FR-TRX-02: every balance change records exactly one ledger line. */
class AccountServiceTests {

    private static final String IBAN = "IT60X0542811101000000000001";

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CurrentCustomerResolver currentCustomerResolver = mock(CurrentCustomerResolver.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);

    private AccountService accountService;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, currentCustomerResolver, transactionRepository);
        CustomerEntity owner = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .iban(IBAN)
                .balance(new BigDecimal("100.00"))
                .currency(Currency.EUR)
                .status(AccountStatus.ACTIVE)
                .customer(owner)
                .build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(owner);
        when(accountRepository.findByIbanForUpdate(IBAN)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

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
    void rejectedWithdrawRecordsNothing() {
        assertThatThrownBy(() -> accountService.withdraw(IBAN, new CashOperationRequest(new BigDecimal("500.00"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.INSUFFICIENT_FUNDS);

        verify(transactionRepository, never()).save(any());
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void depositOnBlockedAccountRecordsNothing() {
        account.setStatus(AccountStatus.BLOCKED);

        assertThatThrownBy(() -> accountService.deposit(IBAN, new CashOperationRequest(BigDecimal.ONE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.ACCOUNT_BLOCKED);

        verify(transactionRepository, never()).save(any());
    }

    private TransactionEntity savedLine() {
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
