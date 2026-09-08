package com.financedashboard.web.controller;

import com.financedashboard.application.TransactionEditService;
import com.financedashboard.application.TransactionService;
import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.TransactionFilter;
import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.web.dto.CategorizeRequest;
import com.financedashboard.web.dto.PageResponse;
import com.financedashboard.web.dto.TransactionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for reading and editing transactions. */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactions;
    private final TransactionEditService edit;

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

    /**
     * Sets a transaction's category and/or nature. A JSON {@code categoryId} of null clears the
     * category; an absent field leaves it unchanged. Setting {@code nature} to TRANSFER is not
     * allowed here — use the transfers endpoint.
     */
    @PatchMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @RequestBody JsonNode body) {
        boolean categorySpecified = body.has("categoryId");
        Long categoryId = categorySpecified && !body.get("categoryId").isNull()
                ? body.get("categoryId").asLong()
                : null;
        TransactionNature nature = body.hasNonNull("nature")
                ? TransactionNature.valueOf(body.get("nature").asText())
                : null;
        return TransactionResponse.from(edit.categorize(id, categorySpecified, categoryId, nature));
    }

    /** Bulk-assigns a category (null clears it) to the listed transactions. */
    @PostMapping("/categorize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void categorize(@Valid @RequestBody CategorizeRequest request) {
        edit.categorizeBulk(request.transactionIds(), request.categoryId());
    }

    /** Deletes transactions in an inclusive date range, optionally restricted to one account. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId) {
        edit.deleteRange(from, to, accountId);
    }
}
