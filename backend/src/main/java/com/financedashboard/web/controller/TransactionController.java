package com.financedashboard.web.controller;

import com.financedashboard.application.TransactionService;
import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.TransactionFilter;
import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.web.dto.PageResponse;
import com.financedashboard.web.dto.TransactionResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for reading transactions. */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactions;

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean uncategorized,
            @RequestParam(required = false) TransactionNature nature,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        TransactionFilter filter = new TransactionFilter(
                accountId, categoryId, uncategorized, nature, from, to, q);
        PagedTransactions result = transactions.list(filter, page, size);
        List<TransactionResponse> content = result.content().stream()
                .map(TransactionResponse::from)
                .toList();
        return new PageResponse<>(
                content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id) {
        return TransactionResponse.from(transactions.get(id));
    }
}
