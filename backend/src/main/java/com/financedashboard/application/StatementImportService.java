package com.financedashboard.application;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountNumbers;
import com.financedashboard.domain.exception.StatementFxRateException;
import com.financedashboard.domain.exception.UnsupportedStatementException;
import com.financedashboard.domain.merchant_rule.MerchantRule;
import com.financedashboard.domain.money.FxMath;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.BankStatementParser;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.FxRateProvider;
import com.financedashboard.domain.port.MerchantRuleRepository;
import com.financedashboard.domain.port.TransactionAmountRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.statement.ParsedStatement;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionAmount;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for importing a bank statement document: selects a parser by content, resolves or
 * creates the owning account from the statement itself, then stores the statement, its
 * transactions idempotently (file hash + per-transaction dedup), and each transaction's value in
 * every supported currency at its own date. An import fails the whole statement when a required FX
 * rate cannot be resolved within the configured lookback window.
 */
@Service
@RequiredArgsConstructor
public class StatementImportService {

    private static final Map<String, String> BANK_LABELS = Map.of("PEKAO", "Pekao");

    private final List<BankStatementParser> parsers;
    private final BankStatementRepository statements;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final FxRateProvider fxRates;
    private final MerchantRuleRepository merchantRules;
    private final TransferSuggestionService transferSuggestions;
    private final TransactionEditService transactionEdit;
    private final TransactionAmountRepository transactionAmounts;
    private final SupportedCurrencies supportedCurrencies;

    @Value("${finance.fx.import.lookback-days:5}")
    private int fxLookbackDays;

    /** Outcome of importing a statement. */
    public record StatementImportResult(
            Long statementId,
            boolean alreadyImported,
            int imported,
            int skipped) {
    }

    @Transactional
    public StatementImportResult importStatement(byte[] file, String mediaType, String fileName) {
        BankStatementParser parser = selectParser(file, mediaType);
        ParsedStatement parsed = parser.parse(file);

        String fileHash = sha256Hex(file);
        if (statements.existsByFileHash(fileHash)) {
            return new StatementImportResult(null, true, 0, 0);
        }

        Account account = resolveAccount(parsed);
        Long accountId = account.getId();

        BankStatement saved = statements.save(BankStatement.builder()
                .accountId(accountId)
                .bank(parsed.bank())
                .periodStart(parsed.periodStart())
                .periodEnd(parsed.periodEnd())
                .fileName(fileName)
                .fileHash(fileHash)
                .build());

        String nativeCurrency = parsed.currency();
        List<Transaction> rows = parsed.transactions().stream()
                .map(tx -> toTransaction(tx, nativeCurrency, saved.getId(), accountId))
                .toList();

        Set<String> existing = transactions.findExistingDedupHashes(accountId,
                rows.stream().map(Transaction::getDedupHash).collect(Collectors.toSet()));
        List<Transaction> fresh = rows.stream()
                .filter(tx -> !existing.contains(tx.getDedupHash()))
                .toList();
        Map<String, Long> ruleCategory = merchantRules.findAll().stream()
                .collect(Collectors.toMap(MerchantRule::getMerchant, MerchantRule::getCategoryId));
        List<Transaction> tagged = fresh.stream()
                .map(tx -> applyMerchantRules(tx, ruleCategory))
                .toList();

        List<Transaction> persisted = List.of();
        if (!tagged.isEmpty()) {
            Map<CodeDate, BigDecimal> ratePlan = resolveRatePlan(nativeCurrency, tagged);
            persisted = transactions.saveAll(tagged);
            persistMultiCurrencyValues(persisted, nativeCurrency, ratePlan);
        }
        List<Long> freshIds = persisted.stream().map(Transaction::getId).toList();
        if (!freshIds.isEmpty()) {
            transferSuggestions.autoPairForImported(freshIds, transactionEdit);
        }

        return new StatementImportResult(saved.getId(), false, fresh.size(), rows.size() - fresh.size());
    }

    /**
     * Resolves, up front and within the import lookback window, the PLN rate for every
     * (currency, date) needed to value the fresh rows in every supported currency. The base currency
     * always converts at a rate of one and is never looked up. Throws {@link StatementFxRateException}
     * when a required rate is missing or older than the window, or the rate source fails.
     */
    private Map<CodeDate, BigDecimal> resolveRatePlan(String nativeCurrency, List<Transaction> rows) {
        String base = supportedCurrencies.base();
        Set<String> needed = new HashSet<>(supportedCurrencies.codes());
        needed.add(nativeCurrency.toUpperCase(java.util.Locale.ROOT));
        needed.remove(base);

        Set<LocalDate> dates = rows.stream()
                .map(Transaction::getTransactionDate)
                .collect(Collectors.toSet());

        Map<CodeDate, BigDecimal> plan = new HashMap<>();
        for (String code : needed) {
            for (LocalDate date : dates) {
                CodeDate key = new CodeDate(code, date);
                plan.put(key, resolveRate(code, date));
            }
        }
        return plan;
    }

    private BigDecimal resolveRate(String code, LocalDate date) {
        final FxRateProvider.FxRate rate;
        try {
            rate = fxRates.findRate(code, date).orElse(null);
        } catch (RuntimeException e) {
            throw new StatementFxRateException(
                    "Could not fetch the " + code + " exchange rate for " + date
                            + "; the rate source is unavailable and the statement was not imported.",
                    e);
        }
        LocalDate oldestAllowed = date.minusDays(fxLookbackDays);
        if (rate == null || rate.date().isBefore(oldestAllowed)) {
            throw new StatementFxRateException(
                    "No " + code + " exchange rate on or within " + fxLookbackDays
                            + " days before " + date + "; the statement was not imported.");
        }
        return rate.rate();
    }

    private void persistMultiCurrencyValues(List<Transaction> persisted, String nativeCurrency,
                                            Map<CodeDate, BigDecimal> ratePlan) {
        String base = supportedCurrencies.base();
        List<TransactionAmount> children = new ArrayList<>();
        for (Transaction transaction : persisted) {
            Long id = transaction.getId();
            BigDecimal nativeAmount = transaction.getAmount();
            for (String code : supportedCurrencies.codes()) {
                if (code.equalsIgnoreCase(nativeCurrency)) {
                    children.add(new TransactionAmount(id, code, nativeAmount));
                    continue;
                }
                BigDecimal nativeToBase = ratePlan.get(new CodeDate(
                        nativeCurrency.toUpperCase(java.util.Locale.ROOT), transaction.getTransactionDate()));
                BigDecimal targetToBase = base.equals(code)
                        ? BigDecimal.ONE
                        : ratePlan.get(new CodeDate(code, transaction.getTransactionDate()));
                children.add(new TransactionAmount(id, code,
                        FxMath.inTarget(nativeAmount, nativeToBase, targetToBase)));
            }
        }
        transactionAmounts.saveAll(children);
    }

    /**
     * Resolves the account a parsed statement belongs to. A statement that names its account number
     * is matched to the stored account with that number (reused, currency must agree); otherwise
     * the account is created automatically so that currency always follows the statement. When the
     * statement names no account number and exactly one account exists in that currency, it is
     * reused; otherwise a new account is created.
     */
    private Account resolveAccount(ParsedStatement parsed) {
        String currency = parsed.currency().toUpperCase(java.util.Locale.ROOT);
        if (parsed.accountNumber() != null) {
            Optional<Account> byNumber = accounts.findByAccountNumber(parsed.accountNumber());
            if (byNumber.isPresent()) {
                Account existing = byNumber.get();
                if (!existing.getCurrency().equals(currency)) {
                    throw new IllegalArgumentException("Statement currency " + parsed.currency()
                            + " conflicts with the stored currency of that account, "
                            + existing.getCurrency());
                }
                return existing;
            }
            return accounts.save(newAccount(parsed, currency, parsed.accountNumber()));
        }
        return accounts.findFirstByCurrency(currency)
                .orElseGet(() -> accounts.save(newAccount(parsed, currency, null)));
    }

    private static Account newAccount(ParsedStatement parsed, String currency, String accountNumber) {
        return Account.builder()
                .name(accountName(parsed, accountNumber))
                .currency(currency)
                .accountNumber(accountNumber)
                .sortOrder(0)
                .build();
    }

    /** Default label for an account created from a statement: the bank and the number's tail. */
    private static String accountName(ParsedStatement parsed, String accountNumber) {
        String label = BANK_LABELS.getOrDefault(parsed.bank(), parsed.bank());
        if (accountNumber == null) {
            return label + " " + parsed.currency();
        }
        return label + " •••• " + AccountNumbers.lastDigits(accountNumber, 4);
    }

    private static Transaction applyMerchantRules(Transaction transaction, Map<String, Long> ruleCategory) {
        if (ruleCategory.isEmpty() || transaction.getCategoryId() != null) {
            return transaction;
        }
        String merchant = transaction.getMerchant();
        if (merchant == null || merchant.isBlank()) {
            return transaction;
        }
        Long categoryId = ruleCategory.get(MerchantRuleService.normalize(merchant));
        return categoryId == null ? transaction : transaction.toBuilder().categoryId(categoryId).build();
    }

    private Transaction toTransaction(com.financedashboard.domain.statement.ParsedTransaction tx,
                                      String currency, Long statementId, Long accountId) {
        return Transaction.builder()
                .statementId(statementId)
                .accountId(accountId)
                .transactionDate(tx.date())
                .amount(tx.amount())
                .currency(currency)
                .nature(TransactionNature.forSignedAmount(tx.amount()))
                .description(tx.description())
                .merchant(tx.merchant())
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

    private static String dedupHash(LocalDate date, BigDecimal amount, String currency, String description) {
        String canonical = String.join("|",
                date.toString(),
                amount.toPlainString(),
                currency,
                description == null ? "" : description.trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** A rate lookup key: the currency and the transaction date the rate is required for. */
    private record CodeDate(String code, LocalDate date) {
    }
}
