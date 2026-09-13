package com.financedashboard.web.controller;

import com.financedashboard.application.RefundSuggestionService;
import com.financedashboard.application.TransactionEditService;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.web.dto.BulkCountResponse;
import com.financedashboard.web.dto.PairRefundRequest;
import com.financedashboard.web.dto.RefundApplyResponse;
import com.financedashboard.web.dto.RefundPairResponse;
import com.financedashboard.web.dto.RefundSuggestionResponse;
import com.financedashboard.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for linking transactions into refund groups and reverting them. */
@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final TransactionEditService refunds;
    private final RefundSuggestionService suggestions;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundPairResponse pair(@Valid @RequestBody PairRefundRequest request) {
        List<Transaction> legs = refunds.pairRefund(request.transactionIds());
        return new RefundPairResponse(
                legs.get(0).getRefundGroupId(),
                legs.stream().map(TransactionResponse::from).toList());
    }

    /** Lists currently detected purchase-and-refund pairs that are not yet linked. */
    @GetMapping("/suggestions")
    public List<RefundSuggestionResponse> listSuggestions() {
        return suggestions.suggest().stream().map(RefundSuggestionResponse::from).toList();
    }

    /** Applies all current suggestions (each pair becomes a REFUND). */
    @PostMapping("/suggestions/apply")
    public RefundApplyResponse applySuggestions() {
        int applied = suggestions.apply(refunds);
        return new RefundApplyResponse(applied);
    }

    /**
     * Reverts the refund that one of its legs belongs to: every leg becomes a normal income/expense
     * again. Returns how many legs were reverted.
     */
    @PostMapping("/{transactionId}/unlink")
    public BulkCountResponse unlink(@PathVariable Long transactionId) {
        return new BulkCountResponse(refunds.unpairRefund(transactionId).size());
    }
}
