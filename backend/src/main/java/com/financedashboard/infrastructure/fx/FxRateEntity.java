package com.financedashboard.infrastructure.fx;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity backing the {@code fx_rate} table (primary, immutable rate store). */
@Entity
@Table(name = "fx_rate")
@Getter
@Setter
@NoArgsConstructor
public class FxRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal rate;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
