package com.financedashboard.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of money in a given 3-letter ISO currency. Amounts are signed: expenses are
 * negative, income positive.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code");
        }
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    /** Returns the same amount with the sign flipped. */
    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    /**
     * Converts this amount to another currency by multiplying with {@code rate}, rounding to
     * four decimal places.
     */
    public Money convert(BigDecimal rate, String targetCurrency) {
        BigDecimal converted = amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
        return new Money(converted, targetCurrency);
    }
}
