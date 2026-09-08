package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Use cases for reading and filtering transactions. */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final TransactionRepository transactions;

    /** Returns one page of transactions matching the filter. */
    public PagedTransactions list(TransactionFilter filter, Integer page, Integer size) {
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null ? DEFAULT_PAGE_SIZE
                : Math.min(MAX_PAGE_SIZE, Math.max(size, 1));
        return transactions.search(filter, safePage, safeSize);
    }

    /** Returns the transaction with the given id. */
    public Transaction get(Long id) {
        return transactions.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));
    }
}
