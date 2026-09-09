package com.financedashboard.domain.account;

import java.util.Locale;

/**
 * Canonical form for bank account numbers. A number is stored and matched as its digits only, so
 * the leading IBAN country code ({@code PL…}) and any grouping spaces are dropped: full IBANs and
 * the national number of the same account compare equal.
 */
public final class AccountNumbers {

    private AccountNumbers() {
    }

    /** Returns the digits-only canonical form, or null when the input is blank. */
    public static String canonical(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.toUpperCase(Locale.ROOT).replaceAll("[^0-9]", "");
        return compact.isEmpty() ? null : compact;
    }

    /** Returns the last {@code count} digits of a canonical number, without leading padding. */
    public static String lastDigits(String canonical, int count) {
        if (canonical == null) {
            return null;
        }
        int start = Math.max(0, canonical.length() - count);
        return canonical.substring(start);
    }
}
