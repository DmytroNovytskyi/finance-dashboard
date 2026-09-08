package com.financedashboard.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void classifiesSign() {
        assertThat(Money.of(new BigDecimal("-12.50"), "PLN").isNegative()).isTrue();
        assertThat(Money.of(new BigDecimal("12.50"), "PLN").isPositive()).isTrue();
        assertThat(Money.of(BigDecimal.ZERO, "PLN").isNegative()).isFalse();
    }

    @Test
    void negateFlipsSign() {
        Money negated = Money.of(new BigDecimal("-12.50"), "PLN").negate();
        assertThat(negated.amount()).isEqualByComparingTo("12.50");
    }

    @Test
    void convertMultipliesAndRoundsHalfUpToFourPlaces() {
        Money converted = Money.of(new BigDecimal("100"), "USD")
                .convert(new BigDecimal("4.12345"), "PLN");
        assertThat(converted.currency()).isEqualTo("PLN");
        assertThat(converted.amount()).isEqualByComparingTo("412.3450");
    }

    @Test
    void rejectsNullAmountAndBadCurrency() {
        assertThatThrownBy(() -> Money.of(null, "PLN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "XX"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
