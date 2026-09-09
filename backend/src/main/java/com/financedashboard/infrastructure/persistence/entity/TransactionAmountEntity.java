package com.financedashboard.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity backing the {@code transaction_amount} table (composite key transaction + currency). */
@Entity
@Table(name = "transaction_amount")
@Getter
@Setter
@NoArgsConstructor
@IdClass(TransactionAmountEntity.TransactionAmountId.class)
public class TransactionAmountEntity {

    @Id
    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Id
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Composite primary key of the entity. */
    public static class TransactionAmountId implements Serializable {

        private Long transactionId;
        private String currency;

        public TransactionAmountId() {
        }

        public TransactionAmountId(Long transactionId, String currency) {
            this.transactionId = transactionId;
            this.currency = currency;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TransactionAmountId that)) {
                return false;
            }
            return Objects.equals(transactionId, that.transactionId)
                    && Objects.equals(currency, that.currency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(transactionId, currency);
        }
    }
}
