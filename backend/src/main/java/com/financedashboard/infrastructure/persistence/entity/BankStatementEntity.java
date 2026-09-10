package com.financedashboard.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity backing the {@code bank_statement} table. */
@Entity
@Table(name = "bank_statement")
@Getter
@Setter
@NoArgsConstructor
public class BankStatementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** Read-only link to the owning account, used only to order rows by account name. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private AccountEntity account;

    @Column(nullable = false)
    private String bank;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_hash", nullable = false, unique = true)
    private String fileHash;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    @PrePersist
    void onCreate() {
        importedAt = Instant.now();
    }
}
