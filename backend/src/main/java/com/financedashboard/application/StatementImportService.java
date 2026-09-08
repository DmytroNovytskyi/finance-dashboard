package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.exception.UnsupportedStatementException;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.BankStatementParser;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.FxRateProvider;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.statement.ParsedStatement;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Use case for importing a bank statement document for one account: selects a parser by content,
 * parses, stores the statement and its transactions idempotently (file hash + per-transaction
 * dedup).
 */
@Service
@RequiredArgsConstructor
public class StatementImportService {

    private final List<BankStatementParser> parsers;
    private final BankStatementRepository statements;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final FxRateProvider fxRates;

    @Value("${finance.base-currency:PLN}")
    private String baseCurrency;

    /** Outcome of importing a statement. */
    public record StatementImportResult(
            Long statementId,
            boolean alreadyImported,
            int imported,
            int skipped) {
    }

    public StatementImportResult importStatement(byte[] file, String mediaType, Long accountId, String fileName) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account " + accountId + " not found"));

        BankStatementParser parser = selectParser(file, mediaType);
        ParsedStatement parsed = parser.parse(file);

        if (!parsed.currency().equals(account.getCurrency())) {
            throw new IllegalArgumentException("Statement currency " + parsed.currency()
                    + " does not match account currency " + account.getCurrency());
        }

        String fileHash = sha256Hex(file);
        if (statements.existsByFileHash(fileHash)) {
            return new StatementImportResult(null, true, 0, 0);
        }

        BankStatement saved = statements.save(BankStatement.builder()
                .accountId(accountId)
                .bank(parsed.bank())
                .periodStart(parsed.periodStart())
                .periodEnd(parsed.periodEnd())
                .fileName(fileName)
                .fileHash(fileHash)
                .build());

        List<Transaction> rows = parsed.transactions().stream()
                .map(tx -> toTransaction(tx, parsed.currency(), saved.getId(), accountId))
                .toList();

        Set<String> existing = transactions.findExistingDedupHashes(accountId,
                rows.stream().map(Transaction::getDedupHash).collect(Collectors.toSet()));
        List<Transaction> fresh = rows.stream()
                .filter(tx -> !existing.contains(tx.getDedupHash()))
                .toList();
        transactions.saveAll(fresh);

        return new StatementImportResult(saved.getId(), false, fresh.size(), rows.size() - fresh.size());
    }

    private Transaction toTransaction(com.financedashboard.domain.statement.ParsedTransaction tx,
                                      String currency, Long statementId, Long accountId) {
        BigDecimal baseAmount = null;
        BigDecimal fxRate = null;
        LocalDate fxRateDate = null;
        if (currency.equalsIgnoreCase(baseCurrency)) {
            baseAmount = tx.amount();
        } else {
            Optional<FxRateProvider.FxRate> rate;
            try {
                rate = fxRates.findRate(currency, tx.date());
            } catch (RuntimeException e) {
                rate = Optional.empty(); // rate source unavailable: store without conversion
            }
            if (rate.isPresent()) {
                fxRate = rate.get().rate();
                fxRateDate = rate.get().date();
                baseAmount = tx.amount().multiply(fxRate).setScale(4, RoundingMode.HALF_UP);
            }
        }
        return Transaction.builder()
                .statementId(statementId)
                .accountId(accountId)
                .transactionDate(tx.date())
                .amount(tx.amount())
                .currency(currency)
                .nature(TransactionNature.forSignedAmount(tx.amount()))
                .description(tx.description())
                .merchant(tx.merchant())
                .baseAmount(baseAmount)
                .fxRate(fxRate)
                .fxRateDate(fxRateDate)
                .dedupHash(dedupHash(tx.date(), tx.amount(), currency, tx.description()))
                .build();
    }

    private BankStatementParser selectParser(byte[] file, String mediaType) {
        List<BankStatementParser> candidates = parsers;
        if (mediaType != null && !mediaType.isBlank()) {
            List<BankStatementParser> byType =
                    parsers.stream().filter(p -> p.supports(mediaType)).toList();
            if (!byType.isEmpty()) {
                candidates = byType;
            }
        }
        return candidates.stream()
                .filter(p -> p.canParse(file))
                .findFirst()
                .orElseThrow(() -> new UnsupportedStatementException(
                        "No parser recognized the uploaded statement document"));
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String dedupHash(LocalDate date, java.math.BigDecimal amount, String currency, String description) {
        String canonical = String.join("|",
                date.toString(),
                amount.toPlainString(),
                currency,
                description == null ? "" : description.trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
