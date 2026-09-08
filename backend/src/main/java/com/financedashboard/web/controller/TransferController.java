package com.financedashboard.web.controller;

import com.financedashboard.application.TransactionEditService;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.web.dto.PairTransferRequest;
import com.financedashboard.web.dto.TransactionResponse;
import com.financedashboard.web.dto.TransferPairResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
