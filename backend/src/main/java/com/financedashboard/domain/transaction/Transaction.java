package com.financedashboard.domain.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** A single normalized transaction imported from a bank statement. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Transaction {

    private final Long id;
    private final Long statementId;
    private final Long accountId;
    private final LocalDate transactionDate;
    private final BigDecimal amount;
    private final String currency;
    private final TransactionNature nature;
    private final String description;
    private final String merchant;
    private final Long categoryId;
    private final String dedupHash;
    private final BigDecimal baseAmount;
    private final BigDecimal fxRate;
    private final LocalDate fxRateDate;
    private final UUID transferGroupId;
}
