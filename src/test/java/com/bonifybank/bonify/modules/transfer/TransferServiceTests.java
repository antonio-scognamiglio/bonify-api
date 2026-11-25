package com.bonifybank.bonify.modules.transfer;

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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.account.AccountEntity;
import com.bonifybank.bonify.modules.account.AccountRepository;
import com.bonifybank.bonify.modules.account.AccountStatus;
import com.bonifybank.bonify.modules.customer.CurrentCustomerResolver;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.TransactionRepository;
import com.bonifybank.bonify.modules.transfer.dto.TransferRequest;
import com.bonifybank.bonify.modules.transfer.dto.TransferResult;

/** FR-TRF-01 / FR-TRF-02: UUID idempotency key, new transfer vs idempotent retry. */
class TransferServiceTests {

    private static final String FROM = "IT60X0542811101000000000001";
    private static final String TO = "IT60X0542811101000000000002";
    private static final String KEY = "6f1c2a8e-3b4d-4e5f-9a10-1b2c3d4e5f60";

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CurrentCustomerResolver currentCustomerResolver = mock(CurrentCustomerResolver.class);

    private TransferService transferService;
    private CustomerEntity owner;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(transferRepository, accountRepository, transactionRepository,
                currentCustomerResolver);
        owner = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        CustomerEntity receiver = CustomerEntity.builder().id(UUID.randomUUID()).role(CustomerRole.CUSTOMER).build();
        when(currentCustomerResolver.currentCustomer()).thenReturn(owner);
        when(accountRepository.findByIbanForUpdate(FROM)).thenReturn(Optional.of(account(FROM, "500.00", owner)));
        when(accountRepository.findByIbanForUpdate(TO)).thenReturn(Optional.of(account(TO, "0.00", receiver)));
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

    private static TransferRequest request(String amount) {
        return new TransferRequest(FROM, TO, new BigDecimal(amount), "rent");
    }

    private static AccountEntity account(String iban, String balance, CustomerEntity customer) {
        return AccountEntity.builder()
                .id(UUID.randomUUID())
                .iban(iban)
                .balance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE)
                .customer(customer)
                .build();
    }
}
