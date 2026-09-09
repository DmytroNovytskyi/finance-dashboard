package com.financedashboard.domain.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FxMathTest {

    @Test
    void convertsNativeForeignAmountToTheBaseCurrency() {
        BigDecimal converted = FxMath.inTarget(new BigDecimal("-50"), new BigDecimal("4.0"), BigDecimal.ONE);
        assertThat(converted).isEqualByComparingTo("-200.0000");
    }

    @Test
    void convertsNativeBaseAmountToATargetCurrency() {
        BigDecimal converted = FxMath.inTarget(new BigDecimal("-250.50"), BigDecimal.ONE, new BigDecimal("4.0"));
        assertThat(converted).isEqualByComparingTo("-62.6250");
    }

    @Test
    void identityRatesLeaveTheAmountUnchanged() {
        BigDecimal amount = new BigDecimal("-100.0000");
        BigDecimal converted = FxMath.inTarget(amount, new BigDecimal("3.5"), new BigDecimal("3.5"));
        assertThat(converted).isEqualByComparingTo(amount);
    }

    @Test
    void roundsHalfUpToFourDecimalPlaces() {
        BigDecimal converted = FxMath.inTarget(new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("3"));
        assertThat(converted).isEqualByComparingTo("0.6667");
    }

    @Test
    void multipliesThenDividesInsteadOfConvertingASummedTotal() {
        BigDecimal converted = FxMath.inTarget(new BigDecimal("2"), new BigDecimal("2"), new BigDecimal("4"));
        assertThat(converted).isEqualByComparingTo("1.0000");
    }
}
