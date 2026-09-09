package com.financedashboard.web.controller;

import com.financedashboard.application.MerchantRuleService;
import com.financedashboard.web.dto.ClearDefaultsResponse;
import com.financedashboard.web.dto.MerchantRuleApplyResponse;
import com.financedashboard.web.dto.MerchantRuleCreateRequest;
import com.financedashboard.web.dto.MerchantRuleResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for merchant-to-category defaults. */
@RestController
@RequestMapping("/api/v1/merchant-rules")
@RequiredArgsConstructor
public class MerchantRuleController {

    private final MerchantRuleService rules;

    @GetMapping
    public List<MerchantRuleResponse> list() {
        return rules.list().stream().map(MerchantRuleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantRuleResponse create(@Valid @RequestBody MerchantRuleCreateRequest request) {
        return MerchantRuleResponse.from(
                rules.create(request.merchant(), request.categoryId()));
    }

    @PostMapping("/apply")
    public MerchantRuleApplyResponse apply() {
        return new MerchantRuleApplyResponse(rules.applyToUncategorized());
    }

    @PostMapping("/{id}/apply")
    public MerchantRuleApplyResponse applyOne(@PathVariable Long id) {
        return new MerchantRuleApplyResponse(rules.apply(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        rules.delete(id);
    }

    /** Deletes every default and reverts the transactions it had auto-tagged. */
    @DeleteMapping
    public ClearDefaultsResponse clearAll() {
        MerchantRuleService.ClearAllResult result = rules.clearAll();
        return new ClearDefaultsResponse(result.rulesRemoved(), result.transactionsUncategorized());
    }
}
