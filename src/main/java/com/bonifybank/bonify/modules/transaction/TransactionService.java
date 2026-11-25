package com.bonifybank.bonify.modules.transaction;

import com.bonifybank.bonify.exception.BusinessException;
import com.bonifybank.bonify.exception.ErrorCodes;
import com.bonifybank.bonify.modules.account.AccountEntity;
import com.bonifybank.bonify.modules.account.AccountRepository;
import com.bonifybank.bonify.modules.customer.CurrentCustomerResolver;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.common.dto.PagedResponse;
import com.bonifybank.bonify.modules.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CurrentCustomerResolver currentCustomerResolver;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CurrentCustomerResolver currentCustomerResolver
    ){
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.currentCustomerResolver = currentCustomerResolver;
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactions(
            String iban,
            int page,
            int size,
            LocalDate from,
            LocalDate to) {

        AccountEntity account = accountRepository.findByIban(iban)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Account not found for IBAN: " + iban));

        CustomerEntity current = currentCustomerResolver.currentCustomer();
        boolean isAdmin = current.getRole() == CustomerRole.ADMIN;
        boolean isOwner = account.getCustomer().getId().equals(current.getId());

        if (!isAdmin && !isOwner) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Access denied to transactions for IBAN: " + iban);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Long accountId = account.getId();

        ZoneId romeZone = ZoneId.of("Europe/Rome");

        Instant fromInstant = from != null
                ? from.atStartOfDay(romeZone).toInstant()
                : null;
        Instant toInstant = to != null
                ? to.plusDays(1).atStartOfDay(romeZone).minusNanos(1).toInstant()
                : null;

        Page<TransactionEntity> resultPage;
        if (fromInstant != null && toInstant != null) {
            if (fromInstant.isAfter(toInstant)) {
                throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "'from' date must not be after 'to' date");
            }
            resultPage = transactionRepository.findByAccount_IdAndCreatedAtBetween(accountId, fromInstant, toInstant, pageable);
        } else if (fromInstant != null) {
            resultPage = transactionRepository.findByAccount_IdAndCreatedAtGreaterThanEqual(accountId, fromInstant, pageable);
        } else if (toInstant != null) {
            resultPage = transactionRepository.findByAccount_IdAndCreatedAtLessThanEqual(accountId, toInstant, pageable);
        } else {
            resultPage = transactionRepository.findByAccount_Id(accountId, pageable);
        }

        return PagedResponse.from(resultPage, this::toResponse);
    }

    private TransactionResponse toResponse(TransactionEntity tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getTransactionType().name(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getDescription(),
                // Only transfer lines have one; reading the id of the lazy proxy needs no extra query
                tx.getTransfer() != null ? tx.getTransfer().getId() : null,
                tx.getCreatedAt()
        );
    }
}
