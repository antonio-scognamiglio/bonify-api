package com.bonifybank.bonify.modules.transfer;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class TransferService {

    // Canonical 8-4-4-4-12 form (UUID.fromString is too lenient, e.g. it accepts "1-1-1-1-1")
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentCustomerResolver currentCustomerResolver;

    public TransferService(
            TransferRepository transferRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            CurrentCustomerResolver currentCustomerResolver) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.transactionRepository = transactionRepository;
        this.currentCustomerResolver = currentCustomerResolver;
    }

    @Transactional
    public TransferResult sendTransfer(String idempotencyKey, TransferRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Idempotency-Key header is required");
        }
        // FR-TRF-01: the key is a UUID. Lower case, so the same UUID in another case is the same key
        String normalizedKey = idempotencyKey.trim().toLowerCase(Locale.ROOT);
        if (!UUID_PATTERN.matcher(normalizedKey).matches()) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Idempotency-Key must be a UUID");
        }

        String sourceIban = request.fromIban().trim();
        String targetIban = request.toIban().trim();
        String requestFingerPrint = fingerprint(sourceIban, targetIban, request.amount(), request.description());

        CustomerEntity current = currentCustomerResolver.currentCustomer();
        Optional<TransferEntity> existing = transferRepository.findByIdempotencyKey(normalizedKey);

        if (existing.isPresent()) {
            TransferEntity savedTransfer = existing.get();

            // The key belongs to the customer who sent the original transfer: anyone else gets the same
            // 409 as a different body, so the original transfer is never revealed (BR-06)
            boolean sameSender = savedTransfer.getFromAccount().getCustomer().getId().equals(current.getId());
            if (!sameSender || !savedTransfer.getRequest().equals(requestFingerPrint)) {
                throw new BusinessException(ErrorCodes.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key already used with different transfer payload");
            }
            // FR-TRF-02: idempotent retry, nothing moves again
            return new TransferResult(toResponse(savedTransfer), false);
        }

        if (sourceIban.equalsIgnoreCase(targetIban)) {
            throw new BusinessException(ErrorCodes.SAME_ACCOUNT, "Source and target IBAN must be different");
        }

        List<String> orderedIbans = Stream.of(sourceIban, targetIban)
                .sorted(Comparator.naturalOrder())
                .toList();

        AccountEntity firstLocked = getAccountForUpdate(orderedIbans.get(0));
        AccountEntity secondLocked = getAccountForUpdate(orderedIbans.get(1));

        AccountEntity fromAccount = firstLocked.getIban().equals(sourceIban) ? firstLocked : secondLocked;
        AccountEntity toAccount = firstLocked.getIban().equals(targetIban) ? firstLocked : secondLocked;

        if (!fromAccount.getCustomer().getId().equals(current.getId())) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Source account does not belong to authenticated customer");
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE || toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCodes.ACCOUNT_BLOCKED, "Both accounts must be ACTIVE");
        }

        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Currency mismatch between source and target accounts");
        }

        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException(ErrorCodes.INSUFFICIENT_FUNDS, "Insufficient balance");
        }

        BigDecimal newFromBalance = fromAccount.getBalance().subtract(request.amount());
        BigDecimal newToBalance = toAccount.getBalance().add(request.amount());

        fromAccount.setBalance(newFromBalance);
        toAccount.setBalance(newToBalance);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        String description = request.description() != null ? request.description().trim() : null;

        TransferEntity transfer = TransferEntity.builder()
                .amount(request.amount())
                .description(description)
                .idempotencyKey(normalizedKey)
                .request(requestFingerPrint)
                .status(Status.COMPLETED)
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .build();

        TransferEntity savedTransfer = transferRepository.save(transfer);

        TransactionEntity outgoingTx = TransactionEntity.builder()
                .account(fromAccount)
                .transactionType(TransactionType.TRANSFER_OUT)
                .amount(request.amount())
                .balanceAfter(newFromBalance)
                .transfer(savedTransfer)
                .description(description)
                .build();

        TransactionEntity incomingTx = TransactionEntity.builder()
                .account(toAccount)
                .transactionType(TransactionType.TRANSFER_IN)
                .amount(request.amount())
                .balanceAfter(newToBalance)
                .transfer(savedTransfer)
                .description(description)
                .build();

        transactionRepository.save(outgoingTx);
        transactionRepository.save(incomingTx);

        return new TransferResult(toResponse(savedTransfer), true);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID id) {
        TransferEntity transfer = transferRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Transfer not found: " + id));

        CustomerEntity current = currentCustomerResolver.currentCustomer();
        boolean isAdmin = current.getRole() == CustomerRole.ADMIN;
        boolean isParticipant = transfer.getFromAccount().getCustomer().getId().equals(current.getId())
                || transfer.getToAccount().getCustomer().getId().equals(current.getId());

        if (!isAdmin && !isParticipant) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Access denied to transfer: " + id);
        }
        return toResponse(transfer);
    }

    private AccountEntity getAccountForUpdate(String iban) {
        return accountRepository.findByIbanForUpdate(iban)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Account not found: " + iban));
    }

    private String fingerprint(String sourceIban, String targetIban, BigDecimal amount, String description) {
        String raw = sourceIban + "|" + targetIban + "|" + amount.toPlainString() + "|" + (description != null ? description.trim() : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 64);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private TransferResponse toResponse(TransferEntity transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromAccount().getIban(),
                transfer.getToAccount().getIban(),
                transfer.getAmount(),
                transfer.getDescription(),
                transfer.getStatus().name(),
                transfer.getCreatedAt()
        );
    }
}
