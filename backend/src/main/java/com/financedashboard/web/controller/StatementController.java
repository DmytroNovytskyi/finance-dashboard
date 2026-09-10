package com.financedashboard.web.controller;

import com.financedashboard.application.StatementCoverageService;
import com.financedashboard.application.StatementImportService;
import com.financedashboard.application.StatementService;
import com.financedashboard.application.TransactionEditService;
import com.financedashboard.domain.exception.StatementParseException;
import com.financedashboard.domain.statement.StatementOrder;
import com.financedashboard.domain.statement.StatementSortField;
import com.financedashboard.web.dto.StatementCoverageResponse;
import com.financedashboard.web.dto.StatementImportResponse;
import com.financedashboard.web.dto.StatementResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** REST endpoints for listing, importing, and deleting bank statements. */
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementImportService importService;
    private final StatementService statements;
    private final StatementCoverageService coverageService;
    private final TransactionEditService edits;

    @GetMapping
    public List<StatementResponse> list(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Long accountId) {
        return statements.list(toOrder(sort, order), accountId).stream()
                .map(StatementResponse::from)
                .toList();
    }

    @GetMapping("/coverage")
    public List<StatementCoverageResponse> coverage() {
        return coverageService.coverage().stream().map(StatementCoverageResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StatementImportResponse importStatement(@RequestPart("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new StatementParseException("Could not read the uploaded file", e);
        }
        return StatementImportResponse.from(importService.importStatement(
                bytes, file.getContentType(), file.getOriginalFilename()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        edits.deleteStatement(id);
    }

    /** Maps the sort and order query parameters onto an ordering, defaulting to newest import. */
    private static StatementOrder toOrder(String sort, String order) {
        StatementSortField field = StatementSortField.IMPORTED;
        if (sort != null) {
            for (StatementSortField candidate : StatementSortField.values()) {
                if (candidate.name().equalsIgnoreCase(sort)) {
                    field = candidate;
                    break;
                }
            }
        }
        return new StatementOrder(field, "asc".equalsIgnoreCase(order));
    }
}
