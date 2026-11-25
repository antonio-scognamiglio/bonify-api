package com.bonifybank.bonify.modules.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    Optional<AccountEntity> findByIban(String iban);

    boolean existsByIban(String iban);

    // Id only, no entity loaded: used to decide the lock order before locking (BR-05)
    @Query("SELECT a.id FROM AccountEntity a WHERE a.iban = :iban")
    Optional<Long> findIdByIban(@Param("iban") String iban);

    List<AccountEntity> findByCustomer_Id(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.iban = :iban")
    Optional<AccountEntity> findByIbanForUpdate(@Param("iban")String iban);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.id = :id")
    Optional<AccountEntity> findByIdForUpdate(@Param("id") Long id);
}
