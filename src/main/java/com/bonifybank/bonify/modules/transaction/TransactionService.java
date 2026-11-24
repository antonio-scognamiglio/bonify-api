package com.bonifybank.bonify.modules.transaction;

import com.bonifybank.bonify.modules.account.AccountEntity;
import com.bonifybank.bonify.modules.account.AccountRepository;
import com.bonifybank.bonify.modules.account.dto.AccountResponse;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import com.bonifybank.bonify.modules.transaction.dto.PagedResponse;
import com.bonifybank.bonify.modules.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CustomerRepository customerRepository
    ){
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactions(
            String iban,
            int page,
            int size,
            LocalDate from,
            LocalDate to) {

        // TO DO REPLACE EXCEPTION
        AccountEntity account = accountRepository.findByIban(iban)
                .orElseThrow(() -> new IllegalArgumentException("Account not found for IBAN: " + iban));

        CustomerEntity current = getCurrentCustomer();
        boolean isAdmin = current.getRole() == CustomerRole.ADMIN;
        boolean isOwner = account.getCustomer().getId().equals(current.getId());

        // TO DO REPLACE EXCEPTION
        if (!isAdmin && !isOwner) {
            throw new SecurityException("Access denied to transactions for IBAN: " + iban);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        UUID accountId = account.getId();

        ZoneId romeZone = ZoneId.of("Europe/Rome");

        Instant fromInstant = from != null
                ? from.atStartOfDay(romeZone).toInstant()
                : null;
        Instant toInstant = to != null
                ? to.plusDays(1).atStartOfDay(romeZone).minusNanos(1).toInstant()
                : null;

        Page<TransactionEntity> resultPage;
        if (fromInstant != null && toInstant != null) {
            // TO DO REPLACE EXCEPTION
            if (fromInstant.isAfter(toInstant)) {
                throw new IllegalArgumentException("'from' date must not be after 'to' date");
            }
            resultPage = transactionRepository.findByAccount_IdAndCreatedAtBetween(accountId, fromInstant, toInstant, pageable);
        } else if (fromInstant != null) {
            resultPage = transactionRepository.findByAccount_IdAndCreatedAtGreaterThanEqual(accountId, fromInstant, pageable);
        } else if (toInstant != null) {
            resultPage = transactionRepository.findByAccount_IdAndCreatedAtLessThanEqual(accountId, toInstant, pageable);
        } else {
            resultPage = transactionRepository.findByAccount_Id(accountId, pageable);
        }

        return new PagedResponse<>(
                resultPage.getContent().stream().map(this::toResponse).toList(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.isLast()
        );
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

    private TransactionResponse toResponse(TransactionEntity tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getTransactionType().name(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }
}
