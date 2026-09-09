package com.financedashboard.web.controller;

import com.financedashboard.application.TransactionEditService;
import com.financedashboard.application.TransferSuggestionService;
import com.financedashboard.application.TransferSuggestionService.SuggestedTransfer;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.web.dto.BulkCountResponse;
import com.financedashboard.web.dto.PairTransferRequest;
import com.financedashboard.web.dto.TransactionResponse;
import com.financedashboard.web.dto.TransferApplyResponse;
import com.financedashboard.web.dto.TransferPairResponse;
import com.financedashboard.web.dto.TransferSuggestionResponse;
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

/** REST endpoints for pairing internal transfers between the user's own accounts. */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransactionEditService transfers;
    private final TransferSuggestionService suggestions;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferPairResponse pair(@Valid @RequestBody PairTransferRequest request) {
        List<Transaction> legs = transfers.pairTransfer(
                request.fromTransactionId(), request.toTransactionId());
        return new TransferPairResponse(
                legs.get(0).getTransferGroupId(),
                TransactionResponse.from(legs.get(0)),
                TransactionResponse.from(legs.get(1)));
    }

    /** Lists currently detected own-account transfer pairs that are not yet marked as transfers. */
    @GetMapping("/suggestions")
    public List<TransferSuggestionResponse> listSuggestions() {
        return suggestions.suggest().stream().map(TransferSuggestionResponse::from).toList();
    }

    /** Applies all current suggestions (each pair becomes a TRANSFER). */
    @PostMapping("/suggestions/apply")
    public TransferApplyResponse applySuggestions() {
        int applied = suggestions.apply(transfers);
        return new TransferApplyResponse(applied);
    }

    /**
     * Reverts the internal transfer that one of its legs belongs to: every leg becomes a normal
     * income/expense again. Returns how many legs were reverted.
     */
    @PostMapping("/{transactionId}/unlink")
    public BulkCountResponse unlink(@PathVariable Long transactionId) {
        return new BulkCountResponse(transfers.unpairTransfer(transactionId).size());
    }
}
