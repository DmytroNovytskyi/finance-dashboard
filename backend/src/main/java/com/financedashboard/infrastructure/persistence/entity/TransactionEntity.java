package com.financedashboard.infrastructure.persistence.entity;

import com.financedashboard.domain.transaction.TransactionNature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity backing the {@code transaction} table. */
@Entity
@Table(name = "transaction")
@Getter
@Setter
@NoArgsConstructor
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionNature nature;

    @Column(columnDefinition = "text")
    private String description;

    private String merchant;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "dedup_hash")
    private String dedupHash;

    @Column(name = "base_amount", precision = 19, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "fx_rate", precision = 20, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "fx_rate_date")
    private LocalDate fxRateDate;

    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
