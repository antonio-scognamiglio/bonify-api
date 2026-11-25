package com.bonifybank.bonify.modules.account;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.account.dto.AccountResponse;
import com.bonifybank.bonify.modules.account.dto.CashOperationRequest;
import com.bonifybank.bonify.modules.account.dto.OpenAccountRequest;
import com.bonifybank.bonify.modules.account.dto.UpdateAccountStatusRequest;
import com.bonifybank.bonify.modules.customer.CurrentCustomerResolver;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.TransactionEntity;
import com.bonifybank.bonify.modules.transaction.TransactionRepository;
import com.bonifybank.bonify.modules.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CurrentCustomerResolver currentCustomerResolver;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, CurrentCustomerResolver currentCustomerResolver,
            TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.currentCustomerResolver = currentCustomerResolver;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountResponse openAccount(OpenAccountRequest request) {
        CustomerEntity customer = currentCustomerResolver.currentCustomer();
        Currency currency = Currency.valueOf(request.currency().trim().toUpperCase());

        String iban = generateUniqueIban();

        AccountEntity account = AccountEntity.builder()
                .iban(iban)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .status(AccountStatus.ACTIVE)
                .customer(customer)
                .build();

        AccountEntity saved = accountRepository.save(account);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listMyAccounts() {
        CustomerEntity customer = currentCustomerResolver.currentCustomer();
        return accountRepository.findByCustomer_Id(customer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String iban) {
        AccountEntity account = accountRepository.findByIban(iban)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Account not found for IBAN: " + iban));

        CustomerEntity current = currentCustomerResolver.currentCustomer();
        boolean isAdmin = current.getRole() == CustomerRole.ADMIN;
        boolean isOwner = account.getCustomer().getId().equals(current.getId());

        if (!isAdmin && !isOwner) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Access denied to account: " + iban);
        }

        return toResponse(account);
    }

    @Transactional
    public AccountResponse deposit(String iban, CashOperationRequest request) {
        AccountEntity account = getOwnedActiveAccountForUpdate(iban);
        BigDecimal updateBalance = account.getBalance().add(request.amount());
        account.setBalance(updateBalance);

        AccountEntity saved = accountRepository.save(account);
        recordLine(saved, TransactionType.DEPOSIT, request);
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse withdraw(String iban, CashOperationRequest request) {
        AccountEntity account = getOwnedActiveAccountForUpdate(iban);

        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException(ErrorCodes.INSUFFICIENT_FUNDS, "Insufficient balance");
        }

        BigDecimal updateBalance = account.getBalance().subtract(request.amount());
        account.setBalance(updateBalance);

        AccountEntity saved = accountRepository.save(account);
        recordLine(saved, TransactionType.WITHDRAW, request);
        return toResponse(saved);
    }

    // FR-TRX-02: every balance change creates exactly one ledger line, in the same database transaction
    private void recordLine(AccountEntity account, TransactionType type, CashOperationRequest request) {
        String description = request.description() != null ? request.description().trim() : null;
        transactionRepository.save(TransactionEntity.builder()
                .account(account)
                .transactionType(type)
                .amount(request.amount())
                .balanceAfter(account.getBalance())
                .description(description)
                .build());
    }

    @Transactional
    public AccountResponse updateStatus(String iban, UpdateAccountStatusRequest request) {
        AccountEntity account = accountRepository.findByIbanForUpdate(iban)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Account not found for IBAN: " + iban));

        AccountStatus newStatus = AccountStatus.valueOf(request.status().trim().toUpperCase());
        account.setStatus(newStatus);

        AccountEntity saved = accountRepository.save(account);
        return toResponse(saved);
    }

    private AccountEntity getOwnedActiveAccountForUpdate(String iban) {
        AccountEntity account = accountRepository.findByIbanForUpdate(iban)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Account not found for IBAN: " + iban));

        CustomerEntity current = currentCustomerResolver.currentCustomer();
        if (!account.getCustomer().getId().equals(current.getId())) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Account does not belong to authenticated customer");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCodes.ACCOUNT_BLOCKED, "Account is not ACTIVE");
        }

        return account;
    }


    private String generateUniqueIban() {
        String prefix = "IT60X0542811101000000"; // 21 caratteri
        String candidate;
        do {
            int randomSixDigits = ThreadLocalRandom.current().nextInt(0, 1_000_000);
            candidate = prefix + String.format("%06d", randomSixDigits); // 27 caratteri totali
        } while (accountRepository.existsByIban(candidate));
        return candidate;
    }

    private AccountResponse toResponse(AccountEntity account) {
        return new AccountResponse(
                account.getId(),
                account.getIban(),
                account.getCurrency().name(),
                account.getBalance(),
                account.getStatus().name(),
                account.getCustomer().getId(),
                account.getCreatedAt()
        );
    }
}
