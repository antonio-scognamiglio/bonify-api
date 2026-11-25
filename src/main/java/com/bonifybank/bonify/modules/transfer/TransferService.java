package com.bonifybank.bonify.modules.transfer;

import com.bonifybank.bonify.modules.account.AccountEntity;
import com.bonifybank.bonify.modules.account.AccountRepository;
import com.bonifybank.bonify.modules.account.AccountStatus;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.TransactionEntity;
import com.bonifybank.bonify.modules.transaction.TransactionRepository;
import com.bonifybank.bonify.modules.transaction.TransactionType;
import com.bonifybank.bonify.modules.transfer.dto.TransferRequest;
import com.bonifybank.bonify.modules.transfer.dto.TransferResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;

    public TransferService(
            TransferRepository transferRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            CustomerRepository customerRepository){
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public TransferResponse sendTransfer(String idempotencyKey, TransferRequest request){

        //TO DO REPLACE EXCEPTION
        if (idempotencyKey == null || idempotencyKey.isBlank()){
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        String normalizedKey = idempotencyKey.trim();
        String sourceIban = request.sourceIban().trim();
        String targetIban = request.targetIban().trim();
        String requestFingerPrint = fingerprint(sourceIban,targetIban,request.amount(), request.description());

        Optional<TransferEntity> existing = transferRepository.findByIdempotencyKey(normalizedKey);

        if (existing.isPresent()){
            TransferEntity savedTransfer = existing.get();

            //TO DO REPLACE EXCEPTION
            if (!savedTransfer.getRequest().equals(requestFingerPrint)){
                throw new IllegalStateException("Idempotency-Key already used with different transfer payload");
            }
            return  toResponse(savedTransfer);
        }

        // TO DO REPLACE EXCEPTION
        if (sourceIban.equalsIgnoreCase(targetIban)) {
            throw new IllegalArgumentException("Source and target IBAN must be different");
        }

        CustomerEntity current = getCurrentCustomer();

        List<String> orderedIbans = Stream.of(sourceIban, targetIban)
                .sorted(Comparator.naturalOrder())
                .toList();

        AccountEntity firstLocked = getAccountForUpdate(orderedIbans.get(0));
        AccountEntity secondLocked = getAccountForUpdate(orderedIbans.get(1));

        AccountEntity fromAccount = firstLocked.getIban().equals(sourceIban) ? firstLocked : secondLocked;
        AccountEntity toAccount = firstLocked.getIban().equals(targetIban) ? firstLocked : secondLocked;

        // TO DO REPLACE EXCEPTION
        if (!fromAccount.getCustomer().getId().equals(current.getId())) {
            throw new SecurityException("Source account does not belong to authenticated customer");
        }

        // TO DO REPLACE EXCEPTION
        if (fromAccount.getStatus() != AccountStatus.ACTIVE || toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Both accounts must be ACTIVE");
        }

        // TO DO REPLACE EXCEPTION
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new IllegalArgumentException("Currency mismatch between source and target accounts");
        }

        // TO DO REPLACE EXCEPTION
        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
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

        return toResponse(savedTransfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID id){
        //TO DO REPLACE EXCEPTION
        TransferEntity transfer = transferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + id));

        CustomerEntity current = getCurrentCustomer();
        boolean isAdmin = current.getRole() == CustomerRole.ADMIN;
        boolean isParticipant = transfer.getFromAccount().getCustomer().getId().equals(current.getId())
                ||transfer.getToAccount().getCustomer().getId().equals(current.getId());

        //TO DO REPLACE EXCEPTION
        if (!isAdmin && !isParticipant){
            throw new SecurityException("Access denied to transfer: " + id);
        }
        return toResponse(transfer);
    }

    private AccountEntity getAccountForUpdate(String iban){
        return accountRepository.findByIbanForUpdate(iban)
                .orElseThrow(()-> new IllegalArgumentException("Account not found: " + iban));
    }

    private CustomerEntity getCurrentCustomer(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        //TO DO REPLACE EXCEPTION
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated user found");
        }

        String email = authentication.getName().trim().toLowerCase();
        //TO DO REPLACE EXCEPTION
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated customer not found"));
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
