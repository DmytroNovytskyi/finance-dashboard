package com.financedashboard.web.controller;

import com.financedashboard.application.AccountService;
import com.financedashboard.web.dto.AccountCreateRequest;
import com.financedashboard.web.dto.AccountResponse;
import com.financedashboard.web.dto.AccountUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for managing accounts. */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accounts;

    @GetMapping
    public List<AccountResponse> list() {
        return accounts.list().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return AccountResponse.from(accounts.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountCreateRequest request) {
        return AccountResponse.from(accounts.create(
                request.name(), request.currency(), request.kind(), request.accountNumber(), request.sortOrder()));
    }

    @PatchMapping("/{id}")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody AccountUpdateRequest request) {
        return AccountResponse.from(accounts.update(
                id, request.name(), request.currency(), request.kind(), request.accountNumber()));
    }
}
