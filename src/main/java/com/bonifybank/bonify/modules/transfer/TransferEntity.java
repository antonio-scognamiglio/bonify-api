package com.bonifybank.bonify.modules.transfer;

import com.bonifybank.bonify.modules.account.AccountEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
// BR-01 at database level
@Table(name = "transfer", check = @CheckConstraint(name = "chk_transfer_amount", constraint = "amount > 0"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 140)
    private String description;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "request", nullable = false, length = 64)
    private String request;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.COMPLETED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    private AccountEntity fromAccount;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name ="to_account_id", nullable = false)
    private AccountEntity toAccount;
}
