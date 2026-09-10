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

    /** A detected purchase-and-refund pair. {@code merchant} is the purchase's, for display. */
    public record SuggestedRefund(
            Long purchaseTransactionId,
            Long refundTransactionId,
            Long accountId,
            BigDecimal amount,
            String currency,
            String merchant,
            LocalDate purchaseDate,
            LocalDate refundDate,
            String reason) {
    }

    /** Returns current suggestions, newest refund first. */
    public List<SuggestedRefund> suggest() {
        Map<Long, List<Transaction>> purchasesByAccount = new HashMap<>();
        List<Transaction> refunds = new ArrayList<>();
        for (Transaction transaction : transactions.findAllStatistical()) {
            if (transaction.getNature() == TransactionNature.INCOME
                    && mentionsReversedPayment(transaction.getDescription())) {
                refunds.add(transaction);
            } else if (transaction.getNature() == TransactionNature.EXPENSE) {
                purchasesByAccount.computeIfAbsent(transaction.getAccountId(), k -> new ArrayList<>())
                        .add(transaction);
            }
        }

        List<SuggestedRefund> suggestions = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Transaction refund : refunds) {
            if (used.contains(refund.getId())) {
                continue;
            }
            SuggestedRefund best = bestMatch(refund,
                    purchasesByAccount.getOrDefault(refund.getAccountId(), List.of()), used);
            if (best != null) {
                suggestions.add(best);
                used.add(best.purchaseTransactionId());
                used.add(best.refundTransactionId());
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
        try {
            refunds.pairRefund(suggestion.purchaseTransactionId(), suggestion.refundTransactionId());
            return true;
        } catch (IllegalArgumentException | NotFoundException alreadyPairedOrGone) {
            return false;
        }
    }

    private static SuggestedRefund bestMatch(Transaction refund, List<Transaction> purchases, Set<Long> used) {
        LocalDate anchorDate = parseAnchorDate(refund.getDescription());
        String anchorMerchant = parseAnchorMerchant(refund.getDescription());
        BigDecimal magnitude = refund.getAmount().abs();

        Transaction bestPurchase = null;
        int bestScore = -1;
        long bestGap = Long.MAX_VALUE;
        for (Transaction purchase : purchases) {
            if (used.contains(purchase.getId())
                    || !purchase.getCurrency().equals(refund.getCurrency())
                    || purchase.getAmount().abs().compareTo(magnitude) != 0
                    || purchase.getTransactionDate().isAfter(refund.getTransactionDate())) {
                continue;
            }
            long gap = ChronoUnit.DAYS.between(purchase.getTransactionDate(), refund.getTransactionDate());
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
                refund.getId(),
                refund.getAccountId(),
                magnitude,
                refund.getCurrency(),
                collapsed(bestPurchase.getMerchant()),
                bestPurchase.getTransactionDate(),
                refund.getTransactionDate(),
                bestScore > 0 ? "ANCHORED" : "AMOUNT");
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
