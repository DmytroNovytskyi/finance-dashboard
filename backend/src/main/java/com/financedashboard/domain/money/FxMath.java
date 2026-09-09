package com.financedashboard.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Currency conversion between PLN-denominated rate values ({@code R(code)} = PLN per 1 unit). */
public final class FxMath {

    private static final int SCALE = 4;

    private FxMath() {
    }

    /**
     * Expresses a native amount in a target currency given the PLN rate of each: the amount is first
     * brought to the base currency (via {@code nativeToBaseRate}) and then to the target (via
     * {@code targetToBaseRate}), rounded to 4 decimal places. Callers pass {@code amount} through
     * unchanged when the target equals the native currency.
     */
    public static BigDecimal inTarget(BigDecimal nativeAmount, BigDecimal nativeToBaseRate,
                                      BigDecimal targetToBaseRate) {
        return nativeAmount.multiply(nativeToBaseRate)
                .divide(targetToBaseRate, SCALE, RoundingMode.HALF_UP);
    }
}
