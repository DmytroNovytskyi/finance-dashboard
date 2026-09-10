package com.financedashboard.application;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Suggests internal transfers between the user's own accounts. Matching keys, strongest first:
 * a leg's description references another own-account number and the mirror leg in that account
 * references this one (works across currencies, e.g. FX); otherwise an outbound leg explicitly
 * referencing another own account is paired with an inbound leg of equal amount in that account.
 */
@Service
@RequiredArgsConstructor
public class TransferSuggestionService {

    private static final int MAX_DATE_DIFF_DAYS = 7;
    private static final int MIN_CANONICAL_LENGTH = 20;

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    /** A detected candidate pair of one internal transfer. */
    public record SuggestedTransfer(
            Long fromTransactionId,
            Long toTransactionId,
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            String currency,
            LocalDate fromDate,
            LocalDate toDate,
            String reason) {
    }

    /** Returns current suggestions among transactions not yet marked as transfers. */
    public List<SuggestedTransfer> suggest() {
        List<Account> ownAccounts = accounts.findAll();
        Map<Long, String> canonicalByAccount = new HashMap<>();
        for (Account account : ownAccounts) {
            String canonical = canonicalOf(account.getAccountNumber());
            if (canonical.length() >= MIN_CANONICAL_LENGTH) {
                canonicalByAccount.put(account.getId(), canonical);
            }
        }

        Map<Long, List<Transaction>> byAccount = new HashMap<>();
        for (Transaction tx : transactions.findAllStatistical()) {
            byAccount.computeIfAbsent(tx.getAccountId(), k -> new ArrayList<>()).add(tx);
        }

        List<SuggestedTransfer> suggestions = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Long accountId : byAccount.keySet()) {
            for (Transaction outbound : byAccount.get(accountId)) {
                if (used.contains(outbound.getId()) || outbound.getAmount().signum() >= 0) {
                    continue;
                }
                SuggestedTransfer best = null;
                long bestDistance = Long.MAX_VALUE;
                for (Long otherId : byAccount.keySet()) {
                    if (otherId.equals(accountId)) {
                        continue;
                    }
                    String otherCanonical = canonicalByAccount.get(otherId);
                    if (otherCanonical == null || !references(outbound.getDescription(), otherCanonical)) {
                        continue;
                    }
                    String ownCanonical = canonicalByAccount.get(accountId);
                    for (Transaction inbound : byAccount.get(otherId)) {
                        if (used.contains(inbound.getId()) || inbound.getAmount().signum() <= 0) {
                            continue;
                        }
                        long distance = Math.abs(ChronoUnit.DAYS.between(outbound.getTransactionDate(),
                                inbound.getTransactionDate()));
                        if (distance > MAX_DATE_DIFF_DAYS) {
                            continue;
                        }
                        if (outbound.getCategoryId() != null && inbound.getCategoryId() != null) {
                            continue;
                        }
                        boolean mirror = ownCanonical != null && references(inbound.getDescription(), ownCanonical);
                        boolean equalSameCurrency = outbound.getCurrency().equals(inbound.getCurrency())
                                && outbound.getAmount().abs().compareTo(inbound.getAmount().abs()) == 0;
                        if (!mirror && !equalSameCurrency) {
                            continue;
                        }
                        if (distance < bestDistance) {
                            best = new SuggestedTransfer(
                                    outbound.getId(), inbound.getId(),
                                    accountId, otherId,
                                    outbound.getAmount().abs(), outbound.getCurrency(),
                                    outbound.getTransactionDate(), inbound.getTransactionDate(),
                                    mirror ? "MIRROR" : "AMOUNT");
                            bestDistance = distance;
                        }
                    }
                }
                if (best != null) {
                    suggestions.add(best);
                    used.add(best.fromTransactionId());
                    used.add(best.toTransactionId());
                }
            }
        }
        return suggestions;
    }

    /** Applies all current suggestions, skipping any that can no longer be paired. */
    public int apply(TransactionEditService transfers) {
        int applied = 0;
        for (SuggestedTransfer suggestion : suggest()) {
            try {
                transfers.pairTransfer(suggestion.fromTransactionId(), suggestion.toTransactionId());
                applied++;
            } catch (IllegalArgumentException | com.financedashboard.application.exception.NotFoundException ignored) {
                // already paired or vanished since suggestion was computed
            }
        }
        return applied;
    }

    /**
     * Auto-applies, as internal transfers, the mirror pairs among the freshly imported rows whose
     * legs are both still uncategorized. Import-completing a pair is then immediate, while a leg
     * that already carries a category is left as a suggestion for manual review.
     */
    public int autoPairForImported(Collection<Long> freshIds, TransactionEditService transfers) {
        int applied = 0;
        for (SuggestedTransfer suggestion : suggest()) {
            boolean touchesFresh = freshIds.contains(suggestion.fromTransactionId())
                    || freshIds.contains(suggestion.toTransactionId());
            if (!touchesFresh || !"MIRROR".equals(suggestion.reason())) {
                continue;
            }
            if (transfers.pairIfBothUncategorized(suggestion.fromTransactionId(), suggestion.toTransactionId())) {
                applied++;
            }
        }
        return applied;
    }

    private static boolean references(String description, String canonicalAccountNumber) {
        if (description == null) {
            return false;
        }
        return digitsOf(description).contains(canonicalAccountNumber);
    }

    private static String canonicalOf(String accountNumber) {
        return accountNumber == null ? "" : accountNumber.replaceAll("\\D", "");
    }

    private static String digitsOf(String text) {
        return text.replaceAll("\\D", "").toUpperCase(Locale.ROOT);
    }
}
