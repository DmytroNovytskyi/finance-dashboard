package com.financedashboard.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.support.PekaoTestPdf;
import com.financedashboard.web.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class StatementImportApiTest extends AbstractIntegrationTest {

    private long createAccount(String currency) {
        return jdbcTemplate.queryForObject("""
                insert into account (name, currency) values (?, ?) returning id
                """, Long.class, "Account " + currency, currency);
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "wyciag.pdf", "application/pdf",
                PekaoTestPdf.threeTransactions());
    }

    @Test
    void importsStatementAndPersistsTransactions() throws Exception {
        long accountId = createAccount("PLN");

        mockMvc.perform(multipart("/api/v1/statements")
                        .file(pdfFile())
                        .param("accountId", String.valueOf(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadyImported").value(false))
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.statementId").isNumber());

        mockMvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void reimportingSameFileIsIdempotent() throws Exception {
        long accountId = createAccount("PLN");
        MockMultipartFile sameFile = new MockMultipartFile("file", "wyciag.pdf", "application/pdf",
                PekaoTestPdf.threeTransactions());

        mockMvc.perform(multipart("/api/v1/statements")
                        .file(sameFile)
                        .param("accountId", String.valueOf(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(3));

        mockMvc.perform(multipart("/api/v1/statements")
                        .file(sameFile)
                        .param("accountId", String.valueOf(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadyImported").value(true))
                .andExpect(jsonPath("$.imported").value(0));

        mockMvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(accountId)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void rejectsStatementWhoseCurrencyDiffersFromAccount() throws Exception {
        long accountId = createAccount("USD");

        mockMvc.perform(multipart("/api/v1/statements")
                        .file(pdfFile())
                        .param("accountId", String.valueOf(accountId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnrecognizedDocument() throws Exception {
        long accountId = createAccount("PLN");
        MockMultipartFile bogus = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "%PDF-1.4\nnot a real statement".getBytes());

        mockMvc.perform(multipart("/api/v1/statements")
                        .file(bogus)
                        .param("accountId", String.valueOf(accountId)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsUnknownAccount() throws Exception {
        mockMvc.perform(multipart("/api/v1/statements")
                        .file(pdfFile())
                        .param("accountId", "424242"))
                .andExpect(status().isNotFound());
    }
}
