package com.financedashboard.domain.transaction;

import java.math.BigDecimal;

/** The value of one transaction expressed in one currency, baked at import time. */
public record TransactionAmount(Long transactionId, String currency, BigDecimal amount) {
}
