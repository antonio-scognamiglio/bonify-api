package com.bonifybank.bonify.modules.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.bonifybank.bonify.common.dto.PagedResponse;
import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.account.AccountEntity;
import com.bonifybank.bonify.modules.account.AccountRepository;
import com.bonifybank.bonify.modules.customer.CurrentCustomerResolver;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.dto.TransactionResponse;
import com.bonifybank.bonify.modules.transfer.TransferEntity;

/** FR-TRX-01: account history, access control, inclusive date range in Europe/Rome, newest first. */
class TransactionServiceTests {

    private static final String IBAN = "IT60X0542811101000000000001";

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CurrentCustomerResolver currentCustomerResolver = mock(CurrentCustomerResolver.class);

    private TransactionService transactionService;
    private CustomerEntity owner;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, accountRepository, currentCustomerResolver);
        owner = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        account = AccountEntity.builder().id(1L).iban(IBAN).customer(owner).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(owner);
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccount_Id(any(), any(Pageable.class))).thenReturn(Page.empty());
        when(transactionRepository.findByAccount_IdAndCreatedAtGreaterThanEqual(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(transactionRepository.findByAccount_IdAndCreatedAtLessThanEqual(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(transactionRepository.findByAccount_IdAndCreatedAtBetween(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    @Test
    void unknownIbanIsNotFound() {
        assertCode(() -> transactionService.listTransactions("IT60X0542811101000000999999", 0, 20, null, null),
                ErrorCodes.NOT_FOUND);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void anotherCustomerIsForbidden() {
        CustomerEntity other = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(other);

        assertCode(() -> transactionService.listTransactions(IBAN, 0, 20, null, null), ErrorCodes.FORBIDDEN);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void ownerCanListHistory() {
        transactionService.listTransactions(IBAN, 0, 20, null, null);

        verify(transactionRepository).findByAccount_Id(eq(account.getId()), any(Pageable.class));
    }

    @Test
    void adminCanListHistoryOfAnyAccount() {
        CustomerEntity admin = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.ADMIN).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(admin);

        transactionService.listTransactions(IBAN, 0, 20, null, null);

        verify(transactionRepository).findByAccount_Id(eq(account.getId()), any(Pageable.class));
    }

    @Test
    void noDatesQueriesTheWholeHistory() {
        transactionService.listTransactions(IBAN, 0, 20, null, null);

        verify(transactionRepository).findByAccount_Id(eq(account.getId()), any(Pageable.class));
        verifyNoDateQuery();
    }

    @Test
    void onlyFromQueriesFromTheStartOfThatDayInRome() {
        transactionService.listTransactions(IBAN, 0, 20, LocalDate.of(2026, 3, 10), null);

        // Rome is UTC+1 on 10 March 2026 (daylight saving starts on 29 March)
        verify(transactionRepository).findByAccount_IdAndCreatedAtGreaterThanEqual(
                eq(account.getId()), eq(Instant.parse("2026-03-09T23:00:00Z")), any(Pageable.class));
    }

    @Test
    void onlyToQueriesUpToTheLastNanosecondOfThatDayInRome() {
        transactionService.listTransactions(IBAN, 0, 20, null, LocalDate.of(2026, 3, 10));

        verify(transactionRepository).findByAccount_IdAndCreatedAtLessThanEqual(
                eq(account.getId()), eq(Instant.parse("2026-03-10T22:59:59.999999999Z")), any(Pageable.class));
    }

    @Test
    void bothDatesQueryTheInclusiveRange() {
        // Rome is UTC+2 in July
        transactionService.listTransactions(IBAN, 0, 20, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        verify(transactionRepository).findByAccount_IdAndCreatedAtBetween(
                eq(account.getId()),
                eq(Instant.parse("2026-06-30T22:00:00Z")),
                eq(Instant.parse("2026-07-31T21:59:59.999999999Z")),
                any(Pageable.class));
    }

    @Test
    void fromAfterToIsValidationError() {
        assertCode(() -> transactionService.listTransactions(IBAN, 0, 20,
                LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1)), ErrorCodes.VALIDATION_ERROR);

        verifyNoDateQuery();
    }

    @Test
    void fromEqualToIsValid() {
        LocalDate day = LocalDate.of(2026, 7, 1);

        transactionService.listTransactions(IBAN, 0, 20, day, day);

        verify(transactionRepository).findByAccount_IdAndCreatedAtBetween(
                eq(account.getId()),
                eq(Instant.parse("2026-06-30T22:00:00Z")),
                eq(Instant.parse("2026-07-01T21:59:59.999999999Z")),
                any(Pageable.class));
    }

    @Test
    void pageableHasGivenPageAndSizeSortedByCreatedAtDescending() {
        transactionService.listTransactions(IBAN, 3, 50, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findByAccount_Id(eq(account.getId()), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void linesAreMappedAndTransferIdIsSetOnlyForTransferLines() {
        UUID transferId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-01T10:00:00Z");
        TransactionEntity deposit = TransactionEntity.builder()
                .id(1L)
                .account(account)
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("50.00"))
                .balanceAfter(new BigDecimal("150.00"))
                .description("salary")
                .createdAt(createdAt)
                .build();
        TransactionEntity transferLine = TransactionEntity.builder()
                .id(2L)
                .account(account)
                .transactionType(TransactionType.TRANSFER_OUT)
                .amount(new BigDecimal("20.00"))
                .balanceAfter(new BigDecimal("130.00"))
                .transfer(TransferEntity.builder().id(transferId).build())
                .createdAt(createdAt)
                .build();
        when(transactionRepository.findByAccount_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transferLine, deposit)));

        PagedResponse<TransactionResponse> response = transactionService.listTransactions(IBAN, 0, 20, null, null);

        assertThat(response.content()).hasSize(2);
        TransactionResponse out = response.content().get(0);
        assertThat(out.id()).isEqualTo(transferLine.getId());
        assertThat(out.type()).isEqualTo("TRANSFER_OUT");
        assertThat(out.amount()).isEqualByComparingTo("20.00");
        assertThat(out.balanceAfter()).isEqualByComparingTo("130.00");
        assertThat(out.transferId()).isEqualTo(transferId);
        TransactionResponse in = response.content().get(1);
        assertThat(in.type()).isEqualTo("DEPOSIT");
        assertThat(in.amount()).isEqualByComparingTo("50.00");
        assertThat(in.balanceAfter()).isEqualByComparingTo("150.00");
        assertThat(in.description()).isEqualTo("salary");
        assertThat(in.createdAt()).isEqualTo(createdAt);
        assertThat(in.transferId()).isNull();
    }

    private void verifyNoDateQuery() {
        verify(transactionRepository, never())
                .findByAccount_IdAndCreatedAtBetween(any(), any(), any(), any(Pageable.class));
        verify(transactionRepository, never())
                .findByAccount_IdAndCreatedAtGreaterThanEqual(any(), any(), any(Pageable.class));
        verify(transactionRepository, never())
                .findByAccount_IdAndCreatedAtLessThanEqual(any(), any(), any(Pageable.class));
    }

    private static void assertCode(ThrowingCallable call, ErrorCodes code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(code);
    }
}
