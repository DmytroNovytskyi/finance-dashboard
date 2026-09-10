package com.financedashboard.application;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.statement.StatementCoverage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read use case reporting how far each account's imported statements reach. */
@Service
@RequiredArgsConstructor
public class StatementCoverageService {

    private final BankStatementRepository statements;
    private final AccountRepository accounts;
    private final Clock clock;

    /**
     * Returns one coverage entry per account, including accounts with no statements so that an
     * account which has never had a statement uploaded is visible rather than missing from the
     * result. The current day is taken from the injected clock.
     */
    public List<StatementCoverage> coverage() {
        LocalDate today = LocalDate.now(clock);
        Map<Long, List<BankStatement>> byAccount = statements.findAllByOrderByPeriodStartAsc().stream()
                .collect(Collectors.groupingBy(BankStatement::getAccountId));
        return accounts.findAll().stream()
                .map(Account::getId)
                .map(accountId -> StatementCoverage.of(
                        accountId, byAccount.getOrDefault(accountId, List.of()), today))
                .toList();
    }
}
