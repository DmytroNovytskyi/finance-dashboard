package com.financedashboard.web.controller;

import com.financedashboard.application.StatementImportService;
import com.financedashboard.domain.exception.StatementParseException;
import com.financedashboard.web.dto.StatementImportResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** REST endpoints for importing bank statements. */
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementImportService importService;

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
}
