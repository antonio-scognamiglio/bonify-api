package com.bonifybank.bonify.modules.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.account.AccountEntity;
import com.bonifybank.bonify.modules.account.AccountRepository;
import com.bonifybank.bonify.modules.account.AccountStatus;
import com.bonifybank.bonify.modules.customer.CurrentCustomerResolver;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.TransactionEntity;
import com.bonifybank.bonify.modules.transaction.TransactionRepository;
import com.bonifybank.bonify.modules.transaction.TransactionType;
import com.bonifybank.bonify.modules.transfer.dto.TransferRequest;
import com.bonifybank.bonify.modules.transfer.dto.TransferResponse;
import com.bonifybank.bonify.modules.transfer.dto.TransferResult;

/** FR-TRF-01 / FR-TRF-02: UUID idempotency key, new transfer vs idempotent retry. */
class TransferServiceTests {

    private static final String FROM = "IT60X0542811101000000000001";
    private static final String TO = "IT60X0542811101000000000002";
    private static final String KEY = "6f1c2a8e-3b4d-4e5f-9a10-1b2c3d4e5f60";
    // Fixed ids so the lock order is deterministic
    private static final Long SMALL_ID = 1L;
    private static final Long LARGE_ID = 2L;
    private static final AtomicLong NEXT_ID = new AtomicLong(100);

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CurrentCustomerResolver currentCustomerResolver = mock(CurrentCustomerResolver.class);

    private TransferService transferService;
    private CustomerEntity owner;
    private CustomerEntity receiver;
    private AccountEntity source;
    private AccountEntity target;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(transferRepository, accountRepository, transactionRepository,
                currentCustomerResolver);
        owner = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        receiver = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(owner);
        source = account(FROM, "500.00", owner);
        target = account(TO, "0.00", receiver);
        source.setId(SMALL_ID);
        target.setId(LARGE_ID);
        stubAccount(source);
        stubAccount(target);
        when(transferRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(transferRepository.save(any())).thenAnswer(invocation -> {
            TransferEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "test-key", "1-1-1-1-1", "6f1c2a8e3b4d4e5f9a101b2c3d4e5f60", "6f1c2a8e-3b4d-4e5f-9a10-1b2c3d4e5f6z" })
    void nonUuidKeyIsValidationError(String key) {
        assertThatThrownBy(() -> transferService.sendTransfer(key, request("150.00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);

        verify(transferRepository, never()).save(any());
    }

    @Test
    void newTransferIsCreatedWithTwoLedgerLines() {
        TransferResult result = transferService.sendTransfer(KEY, request("150.00"));

        assertThat(result.created()).isTrue();
        assertThat(result.transfer().fromIban()).isEqualTo(FROM);
        assertThat(result.transfer().toIban()).isEqualTo(TO);
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void sameKeyAndBodyIsReplayAndMovesNothing() {
        TransferResult first = transferService.sendTransfer(KEY, request("150.00"));
        ArgumentCaptor<TransferEntity> captor = ArgumentCaptor.forClass(TransferEntity.class);
        verify(transferRepository).save(captor.capture());
        // The key is stored in lower case: the same UUID sent in upper case is the same key
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(KEY);
        when(transferRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(captor.getValue()));

        TransferResult replay = transferService.sendTransfer(KEY.toUpperCase(), request("150.00"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.transfer().id()).isEqualTo(first.transfer().id());
        verify(transferRepository, times(1)).save(any());
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void sameKeyDifferentBodyIsConflict() {
        transferService.sendTransfer(KEY, request("150.00"));
        ArgumentCaptor<TransferEntity> captor = ArgumentCaptor.forClass(TransferEntity.class);
        verify(transferRepository).save(captor.capture());
        when(transferRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(captor.getValue()));

        assertThatThrownBy(() -> transferService.sendTransfer(KEY, request("1.00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void sameKeyAndBodyFromAnotherCustomerIsConflictAndRevealsNothing() {
        transferService.sendTransfer(KEY, request("150.00"));
        ArgumentCaptor<TransferEntity> captor = ArgumentCaptor.forClass(TransferEntity.class);
        verify(transferRepository).save(captor.capture());
        when(transferRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(captor.getValue()));
        CustomerEntity other = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(other);

        assertThatThrownBy(() -> transferService.sendTransfer(KEY, request("150.00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCodes.IDEMPOTENCY_KEY_REUSED);
    }


    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   " })
    void missingOrBlankKeyIsValidationError(String key) {
        assertRejected(() -> transferService.sendTransfer(key, request("150.00")), ErrorCodes.VALIDATION_ERROR);
    }

    @Test
    void sameSourceAndDestinationIsRejected() {
        assertRejected(() -> transferService.sendTransfer(KEY, request(FROM, FROM, "150.00")), ErrorCodes.SAME_ACCOUNT);
    }

    @Test
    void unknownSourceIbanIsNotFound() {
        when(accountRepository.findIdByIban(FROM)).thenReturn(Optional.empty());

        assertRejected(() -> transferService.sendTransfer(KEY, request("150.00")), ErrorCodes.NOT_FOUND);
    }

    @Test
    void unknownDestinationIbanIsNotFound() {
        when(accountRepository.findIdByIban(TO)).thenReturn(Optional.empty());

        assertRejected(() -> transferService.sendTransfer(KEY, request("150.00")), ErrorCodes.NOT_FOUND);
    }

    @Test
    void callerNotOwnerOfTheSourceIsForbidden() {
        when(currentCustomerResolver.currentCustomer()).thenReturn(receiver);

        assertRejected(() -> transferService.sendTransfer(KEY, request("150.00")), ErrorCodes.FORBIDDEN);
    }

    @Test
    void blockedSourceIsRejected() {
        source.setStatus(AccountStatus.BLOCKED);

        assertRejected(() -> transferService.sendTransfer(KEY, request("150.00")), ErrorCodes.ACCOUNT_BLOCKED);
    }

    @Test
    void blockedDestinationIsRejected() {
        target.setStatus(AccountStatus.BLOCKED);

        assertRejected(() -> transferService.sendTransfer(KEY, request("150.00")), ErrorCodes.ACCOUNT_BLOCKED);
    }

    @Test
    void amountAboveBalanceIsInsufficientFunds() {
        assertRejected(() -> transferService.sendTransfer(KEY, request("500.01")), ErrorCodes.INSUFFICIENT_FUNDS);
    }

    @Test
    void successMovesMoneyAndRecordsOneLinePerAccountLinkedToTheTransfer() {
        TransferResult result = transferService.sendTransfer(KEY, request(FROM, TO, "150.00", " rent "));

        assertThat(source.getBalance()).isEqualByComparingTo("350.00");
        assertThat(target.getBalance()).isEqualByComparingTo("150.00");
        TransferEntity saved = savedTransfer();
        assertThat(saved.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(saved.getDescription()).isEqualTo("rent");
        assertThat(saved.getAmount()).isEqualByComparingTo("150.00");
        assertThat(result.created()).isTrue();
        assertThat(result.transfer().status()).isEqualTo("COMPLETED");
        assertThat(result.transfer().description()).isEqualTo("rent");

        ArgumentCaptor<TransactionEntity> lines = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository, times(2)).save(lines.capture());
        TransactionEntity out = lineOf(lines.getAllValues(), TransactionType.TRANSFER_OUT);
        TransactionEntity in = lineOf(lines.getAllValues(), TransactionType.TRANSFER_IN);
        assertThat(out.getAccount()).isSameAs(source);
        assertThat(out.getAmount()).isEqualByComparingTo("150.00");
        assertThat(out.getBalanceAfter()).isEqualByComparingTo("350.00");
        assertThat(out.getTransfer()).isSameAs(saved);
        assertThat(in.getAccount()).isSameAs(target);
        assertThat(in.getAmount()).isEqualByComparingTo("150.00");
        assertThat(in.getBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(in.getTransfer()).isSameAs(saved);
    }

    // BR-05: ascending id order, whichever account is the source
    @Test
    void locksTheSmallerIdFirstWhenTheSourceHasTheSmallerId() {
        transferService.sendTransfer(KEY, request("150.00"));

        assertLockOrder(SMALL_ID, LARGE_ID);
    }

    @Test
    void locksTheSmallerIdFirstWhenTheSourceHasTheLargerId() {
        source.setId(LARGE_ID);
        target.setId(SMALL_ID);
        stubAccount(source);
        stubAccount(target);

        transferService.sendTransfer(KEY, request("150.00"));

        assertLockOrder(SMALL_ID, LARGE_ID);
        // The debit still goes to the source, not to the account locked first
        assertThat(source.getBalance()).isEqualByComparingTo("350.00");
        assertThat(target.getBalance()).isEqualByComparingTo("150.00");
    }

    // BR-04: the key is looked up again after the locks, to replay a transfer committed meanwhile
    @Test
    void transferCommittedConcurrentlyWithTheSameKeyIsReplayed() {
        TransferResult first = transferService.sendTransfer(KEY, request("150.00"));
        TransferEntity committed = savedTransfer();
        when(transferRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(committed));

        TransferResult replay = transferService.sendTransfer(KEY, request("150.00"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.transfer().id()).isEqualTo(first.transfer().id());
        verify(transferRepository, times(1)).save(any());
        verify(transactionRepository, times(2)).save(any());
        verify(accountRepository, times(2)).save(any());
        assertThat(source.getBalance()).isEqualByComparingTo("350.00");
        assertThat(target.getBalance()).isEqualByComparingTo("150.00");
    }

    // FR-TRF-03
    @Test
    void senderCanViewTheTransfer() {
        UUID id = storedTransfer();

        assertThat(transferService.getTransfer(id).id()).isEqualTo(id);
    }

    @Test
    void receiverCanViewTheTransfer() {
        UUID id = storedTransfer();
        when(currentCustomerResolver.currentCustomer()).thenReturn(receiver);

        TransferResponse response = transferService.getTransfer(id);

        assertThat(response.fromIban()).isEqualTo(FROM);
        assertThat(response.toIban()).isEqualTo(TO);
        assertThat(response.amount()).isEqualByComparingTo("150.00");
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    void adminCanViewAnyTransfer() {
        UUID id = storedTransfer();
        CustomerEntity admin = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.ADMIN).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(admin);

        assertThat(transferService.getTransfer(id).id()).isEqualTo(id);
    }

    @Test
    void unrelatedCustomerCannotViewTheTransfer() {
        UUID id = storedTransfer();
        CustomerEntity stranger = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(stranger);

        assertCode(() -> transferService.getTransfer(id), ErrorCodes.FORBIDDEN);
    }

    @Test
    void unknownTransferIsNotFound() {
        when(transferRepository.findById(any())).thenReturn(Optional.empty());

        assertCode(() -> transferService.getTransfer(UUID.randomUUID()), ErrorCodes.NOT_FOUND);
    }

    private UUID storedTransfer() {
        UUID id = UUID.randomUUID();
        TransferEntity transfer = TransferEntity.builder()
                .id(id)
                .amount(new BigDecimal("150.00"))
                .idempotencyKey(KEY)
                .request("fingerprint")
                .status(Status.COMPLETED)
                .fromAccount(source)
                .toAccount(target)
                .build();
        when(transferRepository.findById(id)).thenReturn(Optional.of(transfer));
        return id;
    }

    private void assertLockOrder(Long first, Long second) {
        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdForUpdate(first);
        order.verify(accountRepository).findByIdForUpdate(second);
    }

    private TransferEntity savedTransfer() {
        ArgumentCaptor<TransferEntity> captor = ArgumentCaptor.forClass(TransferEntity.class);
        verify(transferRepository).save(captor.capture());
        return captor.getValue();
    }

    private static TransactionEntity lineOf(List<TransactionEntity> lines, TransactionType type) {
        return lines.stream().filter(line -> line.getTransactionType() == type).findFirst().orElseThrow();
    }

    // A rejected request changes nothing: no transfer (so the key is not consumed), no ledger line, same balances
    private void assertRejected(ThrowingCallable call, ErrorCodes code) {
        assertCode(call, code);

        verify(transferRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        assertThat(source.getBalance()).isEqualByComparingTo("500.00");
        assertThat(target.getBalance()).isEqualByComparingTo("0.00");
    }

    private static void assertCode(ThrowingCallable call, ErrorCodes code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(code);
    }

    private void stubAccount(AccountEntity account) {
        when(accountRepository.findIdByIban(account.getIban())).thenReturn(Optional.of(account.getId()));
        when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
    }

    private static TransferRequest request(String amount) {
        return request(FROM, TO, amount);
    }

    private static TransferRequest request(String from, String to, String amount) {
        return request(from, to, amount, "rent");
    }

    private static TransferRequest request(String from, String to, String amount, String description) {
        return new TransferRequest(from, to, new BigDecimal(amount), description);
    }

    private static AccountEntity account(String iban, String balance, CustomerEntity customer) {
        return AccountEntity.builder()
                .id(NEXT_ID.incrementAndGet())
                .iban(iban)
                .balance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE)
                .customer(customer)
                .build();
    }
}
