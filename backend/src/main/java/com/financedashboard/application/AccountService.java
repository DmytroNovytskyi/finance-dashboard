package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.domain.port.AccountRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Use cases for managing the user's accounts. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String CURRENCY_PATTERN = "[A-Z]{3}";

    private final AccountRepository accounts;

    /** Returns all accounts in display order. */
    public List<Account> list() {
        return accounts.findAll();
    }

    /** Returns the account with the given id. */
    public Account get(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("Account " + id + " not found"));
    }

    /** Creates a new account with a normalized name and currency. */
    public Account create(String name, String currency, AccountKind kind, String accountNumber, Integer sortOrder) {
        Account account = Account.builder()
                .name(requireName(name))
                .currency(requireCurrency(currency))
                .kind(kind)
                .accountNumber(accountNumber)
                .sortOrder(sortOrder == null ? 0 : sortOrder)
                .build();
        return accounts.save(account);
    }

    /** Applies the non-null fields to the account with the given id. */
    public Account update(Long id, String name, String currency, AccountKind kind, String accountNumber) {
        Account current = get(id);
        Account account = current.toBuilder()
                .name(name != null ? requireName(name) : current.getName())
                .currency(currency != null ? requireCurrency(currency) : current.getCurrency())
                .kind(kind != null ? kind : current.getKind())
                .accountNumber(accountNumber)
                .build();
        return accounts.save(account);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.trim();
    }

    private static String requireCurrency(String currency) {
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches(CURRENCY_PATTERN)) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code");
        }
        return normalized;
    }
}
