package com.financedashboard.application;

import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.statement.BankStatement;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read use cases for imported statements. */
@Service
@RequiredArgsConstructor
public class StatementService {

    private final BankStatementRepository statements;
    private final TransactionRepository transactions;

    /** A statement together with the number of stored transactions it introduced. */
    public record StatementSummary(BankStatement statement, long transactionCount) {
    }

    /**
     * Returns all imported statements, most recently imported first, each paired with the count of
     * stored transactions it produced.
     */
    public List<StatementSummary> list() {
        List<BankStatement> all = statements.findAllByOrderByImportedAtDesc();
        if (all.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = transactions.countByStatementIds(
                all.stream().map(BankStatement::getId).toList());
        return all.stream()
                .map(statement -> new StatementSummary(
                        statement, counts.getOrDefault(statement.getId(), 0L)))
                .toList();
    }
}
