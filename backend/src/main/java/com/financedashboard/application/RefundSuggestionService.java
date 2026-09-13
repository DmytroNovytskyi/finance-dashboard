package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Suggests refund pairs: a card purchase and the money the bank gave back for it. Netting the two
 * legs out is the only honest reading of the pair — the purchase was reversed, so it is neither
 * spending nor income.
 *
 * <p>A candidate refund leg is an incoming row whose wording says it reverses a payment
 * ({@code ANULOWANIE TRANSAKCJI}, {@code ZWROT ... TRANSAKCJI}). Pekao embeds the reversed
 * transaction in that wording — its date as {@code DN. dd/MM/yyyy} and its merchant after
 * {@code WYKONANEJ:} — which turns the search for the purchase into a lookup rather than a guess.
 * An anchor that does match the purchase outranks one that does not; a refund without any anchor
 * still gets an amount-only suggestion, reported as such so the caller knows it is weaker.
 *
 * <p>Refund wording, not equal amount, is the precondition. The user's own-account settlements and
 * a VAT refund from the tax office both have the shape of a purchase-and-return pair, and matching
 * on amount alone would bury the real ones among them.
 */
@Service
@RequiredArgsConstructor
public class RefundSuggestionService {

    private static final int MAX_AGE_DAYS = 120;
    private static final int DATE_ANCHOR_SCORE = 3;
    private static final int MERCHANT_ANCHOR_SCORE = 3;
    private static final int MIN_MERCHANT_TOKEN_LENGTH = 3;

    private static final DateTimeFormatter ANCHOR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private static final Pattern REFUND_WORDING = Pattern.compile(
            "(?:ZWROT|ANULOWANIE|REFUND|REVERSAL|RETURN).{0,40}?(?:TRANSAKCJI|PŁATNOŚCI|PAYMENT)"
                    + "|(?:TRANSAKCJI|PŁATNOŚCI|PAYMENT).{0,40}?(?:ZWROT|ANULOWANIE|REFUND|REVERSAL|RETURN)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    private static final Pattern ANCHOR_DATE_TEXT = Pattern.compile("DN\\.\\s*(\\d{2}/\\d{2}/\\d{4})");

    private static final Pattern ANCHOR_MERCHANT_TEXT =
            Pattern.compile("WYKONANEJ:\\s*(.+?)\\s+DN\\.", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final TransactionRepository transactions;

    /**
     * A detected reversal: one purchase and the credit or credits that gave it back.
     * {@code merchant} is the purchase's, for display.
     */
    public record SuggestedRefund(
            Long purchaseTransactionId,
            List<Long> refundTransactionIds,
            Long accountId,
            BigDecimal amount,
            String currency,
            String merchant,
            LocalDate purchaseDate,
            LocalDate refundDate,
            String reason) {
    }

    /** What the bank said the reversed transaction was: which account, in what, when, and with whom. */
    private record GroupKey(Long accountId, String currency, LocalDate anchorDate, String merchantToken) {
    }

    /** Returns current suggestions, newest refund first. */
    public List<SuggestedRefund> suggest() {
        Map<Long, List<Transaction>> purchasesByAccount = new HashMap<>();
        Map<GroupKey, List<Transaction>> anchored = new LinkedHashMap<>();
        List<Transaction> unanchored = new ArrayList<>();
        for (Transaction transaction : transactions.findAllStatistical()) {
            if (transaction.getNature() == TransactionNature.INCOME
                    && mentionsReversedPayment(transaction.getDescription())) {
                GroupKey key = groupKey(transaction);
                if (key == null) {
                    unanchored.add(transaction);
                } else {
                    anchored.computeIfAbsent(key, k -> new ArrayList<>()).add(transaction);
                }
            } else if (transaction.getNature() == TransactionNature.EXPENSE) {
                purchasesByAccount.computeIfAbsent(transaction.getAccountId(), k -> new ArrayList<>())
                        .add(transaction);
            }
        }

        List<List<Transaction>> groups = new ArrayList<>(anchored.values());
        for (Transaction refund : unanchored) {
            groups.add(List.of(refund));
        }

        List<SuggestedRefund> suggestions = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (List<Transaction> group : groups) {
            if (group.stream().anyMatch(refund -> used.contains(refund.getId()))) {
                continue;
            }
            SuggestedRefund best = bestMatch(group,
                    purchasesByAccount.getOrDefault(group.get(0).getAccountId(), List.of()), used);
            if (best != null) {
                suggestions.add(best);
                used.add(best.purchaseTransactionId());
                used.addAll(best.refundTransactionIds());
            }
        }
        suggestions.sort(Comparator.comparing(SuggestedRefund::refundDate).reversed());
        return suggestions;
    }

    /** Applies all current suggestions, skipping any that can no longer be paired. */
    public int apply(TransactionEditService refunds) {
        int applied = 0;
        for (SuggestedRefund suggestion : suggest()) {
            if (pairQuietly(refunds, suggestion)) {
                applied++;
            }
        }
        return applied;
    }

    private static boolean pairQuietly(TransactionEditService refunds, SuggestedRefund suggestion) {
        List<Long> ids = new ArrayList<>();
        ids.add(suggestion.purchaseTransactionId());
        ids.addAll(suggestion.refundTransactionIds());
        try {
            refunds.pairRefund(ids);
            return true;
        } catch (IllegalArgumentException | NotFoundException alreadyPairedOrGone) {
            return false;
        }
    }

    /**
     * Finds the purchase a group of refunds reverses. The group's credits must add up to it exactly,
     * which is what rules out a reversal that was only imported in part: half of a refund is not a
     * refund, and netting a partial one out would erase spending that really happened.
     */
    private static SuggestedRefund bestMatch(List<Transaction> group, List<Transaction> purchases, Set<Long> used) {
        BigDecimal total = group.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate firstRefundDate = group.stream()
                .map(Transaction::getTransactionDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDate lastRefundDate = group.stream()
                .map(Transaction::getTransactionDate)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        LocalDate anchorDate = parseAnchorDate(group.get(0).getDescription());
        String anchorMerchant = parseAnchorMerchant(group.get(0).getDescription());

        Transaction bestPurchase = null;
        int bestScore = -1;
        long bestGap = Long.MAX_VALUE;
        for (Transaction purchase : purchases) {
            if (used.contains(purchase.getId())
                    || isDecided(purchase, group)
                    || !purchase.getCurrency().equals(group.get(0).getCurrency())
                    || purchase.getAmount().abs().compareTo(total) != 0
                    || purchase.getTransactionDate().isAfter(firstRefundDate)) {
                continue;
            }
            long gap = ChronoUnit.DAYS.between(purchase.getTransactionDate(), lastRefundDate);
            if (gap > MAX_AGE_DAYS) {
                continue;
            }
            int score = score(purchase, anchorDate, anchorMerchant);
            if (score > bestScore || (score == bestScore && gap < bestGap)) {
                bestPurchase = purchase;
                bestScore = score;
                bestGap = gap;
            }
        }
        if (bestPurchase == null) {
            return null;
        }
        return new SuggestedRefund(
                bestPurchase.getId(),
                group.stream().map(Transaction::getId).toList(),
                group.get(0).getAccountId(),
                total.abs(),
                group.get(0).getCurrency(),
                collapsed(bestPurchase.getMerchant()),
                bestPurchase.getTransactionDate(),
                lastRefundDate,
                bestScore > 0 ? "ANCHORED" : "AMOUNT");
    }

    /**
     * Whether the user has already decided about this reversal by categorizing every leg of it,
     * which is how a transfer stops being suggested. One categorized leg is not a decision: a
     * merchant rule may have set it, and the pair is still mis-stated in the statistics.
     */
    private static boolean isDecided(Transaction purchase, List<Transaction> group) {
        return purchase.getCategoryId() != null
                && group.stream().allMatch(refund -> refund.getCategoryId() != null);
    }

    /**
     * The group a refund belongs to: the credits that all name the same reversed transaction. A
     * refund that does not name one — no date, or no merchant — stands alone rather than being
     * bundled with whatever else came in that day.
     */
    private static GroupKey groupKey(Transaction refund) {
        LocalDate anchorDate = parseAnchorDate(refund.getDescription());
        String anchorMerchant = parseAnchorMerchant(refund.getDescription());
        if (anchorDate == null || anchorMerchant == null) {
            return null;
        }
        return new GroupKey(refund.getAccountId(), refund.getCurrency(), anchorDate, anchorMerchant);
    }

    private static int score(Transaction purchase, LocalDate anchorDate, String anchorMerchant) {
        int score = 0;
        if (anchorDate != null && anchorDate.equals(purchase.getTransactionDate())) {
            score += DATE_ANCHOR_SCORE;
        }
        if (anchorMerchant != null && namesMerchant(purchase, anchorMerchant)) {
            score += MERCHANT_ANCHOR_SCORE;
        }
        return score;
    }

    private static boolean mentionsReversedPayment(String description) {
        return description != null && REFUND_WORDING.matcher(description).find();
    }

    private static LocalDate parseAnchorDate(String description) {
        if (description == null) {
            return null;
        }
        Matcher matcher = ANCHOR_DATE_TEXT.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1), ANCHOR_DATE);
        } catch (DateTimeParseException malformed) {
            return null;
        }
    }

    private static String parseAnchorMerchant(String description) {
        if (description == null) {
            return null;
        }
        Matcher matcher = ANCHOR_MERCHANT_TEXT.matcher(description);
        return matcher.find() ? firstSignificantToken(matcher.group(1)) : null;
    }

    private static boolean namesMerchant(Transaction purchase, String token) {
        return mentions(purchase.getMerchant(), token) || mentions(purchase.getDescription(), token);
    }

    private static boolean mentions(String text, String token) {
        return text != null && normalize(text).contains(token);
    }

    private static String firstSignificantToken(String text) {
        for (String token : normalize(text).split(" ")) {
            if (token.length() >= MIN_MERCHANT_TOKEN_LENGTH) {
                return token;
            }
        }
        return null;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** Statements pad the counterparty with runs of spaces; a display value wants them single. */
    private static String collapsed(String text) {
        return text == null ? null : text.strip().replaceAll("\\s+", " ");
    }
}
