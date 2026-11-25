package com.bonifybank.bonify.modules.account;

import com.bonifybank.bonify.modules.account.dto.AccountResponse;
import com.bonifybank.bonify.modules.account.dto.CashOperationRequest;
import com.bonifybank.bonify.modules.account.dto.OpenAccountRequest;
import com.bonifybank.bonify.modules.account.dto.UpdateAccountStatusRequest;
import com.bonifybank.bonify.modules.customer.CustomerEntity;
import com.bonifybank.bonify.modules.customer.CustomerRepository;
import com.bonifybank.bonify.modules.customer.CustomerRole;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository){
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public AccountResponse openAccount(OpenAccountRequest request){
        CustomerEntity customer = getCurrentCustomer();
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
    public List<AccountResponse> ListMyAccounts(){
        CustomerEntity customer = getCurrentCustomer();
        return accountRepository.findByCustomer_Id(customer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String iban){
        //TO DO REPLACE EXCEPTION
        AccountEntity account = accountRepository.findByIban(iban)
                .orElseThrow(()-> new IllegalArgumentException("Account not found for IBAN: " + iban));

        CustomerEntity current = getCurrentCustomer();
        boolean isAdmin = current.getRole() == CustomerRole.ADMIN;
        boolean isOwner = account.getCustomer().getId().equals(current.getId());

        //TO DO REPLACE EXCEPTION
        if(!isAdmin && !isOwner){
            throw new SecurityException("Access denied to account: " + iban);
        }

        return toResponse(account);
    }

    @Transactional
    public AccountResponse deposit(String iban, CashOperationRequest request){
        AccountEntity account = getOwnedActiveAccountForUpdate(iban);
        BigDecimal updateBalance = account.getBalance().add(request.amount());
        account.setBalance(updateBalance);

        AccountEntity saved = accountRepository.save(account);
        // Qui potrai registrare la transazione di deposito se colleghi il modulo transaction
        return toResponse(saved);
    }

    @Transactional
    public  AccountResponse withdraw(String iban, CashOperationRequest request){
        AccountEntity account = getOwnedActiveAccountForUpdate(iban);

        //TO DO REPLACE EXCEPTION
        if (account.getBalance().compareTo(request.amount()) < 0){
            throw new IllegalArgumentException("Insufficient balance");
        }

        BigDecimal updateBalance = account.getBalance().subtract(request.amount());
        account.setBalance(updateBalance);

        AccountEntity saved = accountRepository.save(account);

        // Qui potrai registrare la transazione di prelievo se colleghi il modulo transaction
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse updateStatus(String iban, UpdateAccountStatusRequest request){

        //TO DO REPLACE EXCEPTION
        AccountEntity account = accountRepository.findByIbanForUpdate(iban)
                .orElseThrow(() -> new IllegalArgumentException("Account not found for IBAN: " + iban));

        AccountStatus newStatus = AccountStatus.valueOf(request.status().trim().toUpperCase());
        account.setStatus(newStatus);

        AccountEntity saved = accountRepository.save(account);
        return  toResponse(saved);
    }


    private AccountEntity getOwnedActiveAccountForUpdate(String iban) {
        //TO DO REPLACCE EXCEPTION
        AccountEntity account = accountRepository.findByIbanForUpdate(iban)
                .orElseThrow(() -> new IllegalArgumentException("Account not found for IBAN: " + iban));

        CustomerEntity current = getCurrentCustomer();
        //TO DO REPLACCE EXCEPTION
        if (!account.getCustomer().getId().equals(current.getId())) {
            throw new SecurityException("Account does not belong to authenticated customer");
        }
        //TO DO REPLACCE EXCEPTION
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not ACTIVE");
        }

        return account;
    }

    //TO DO FIX METHOD
    private CustomerEntity getCurrentCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated user found");
        }

        String email = authentication.getName().trim().toLowerCase();
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated customer not found"));
    }

    //TO DO FIX THIS METHOD
    private String generateUniqueIban() {
        // Sostituisci con il tuo generatore IBAN (max 34 caratteri) verificando l'unicità sul repository
        String candidate;
        do {
            candidate = "IT60X0542811101000000" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        } while (accountRepository.findByIban(candidate).isPresent());
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
