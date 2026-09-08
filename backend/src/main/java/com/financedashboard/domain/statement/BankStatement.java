package com.financedashboard.domain.statement;

import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** One imported statement file, attributed to exactly one account. */
@Getter
@Builder
@AllArgsConstructor
public class BankStatement {

    private final Long id;
    private final Long accountId;
    private final String bank;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final String fileName;
    private final String fileHash;
    private final Instant importedAt;
}
