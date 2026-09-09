package com.financedashboard.web.controller;

import com.financedashboard.application.StatementImportService;
import com.financedashboard.application.StatementService;
import com.financedashboard.application.TransactionEditService;
import com.financedashboard.domain.exception.StatementParseException;
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
    private final TransactionEditService edits;

    @GetMapping
    public List<StatementResponse> list() {
        return statements.list().stream().map(StatementResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StatementImportResponse importStatement(
            @RequestPart("file") MultipartFile file,
            @RequestParam("accountId") Long accountId) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new StatementParseException("Could not read the uploaded file", e);
        }
        return StatementImportResponse.from(importService.importStatement(
                bytes, file.getContentType(), accountId, file.getOriginalFilename()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        edits.deleteStatement(id);
    }
}
