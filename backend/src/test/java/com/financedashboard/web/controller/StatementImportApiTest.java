package com.financedashboard.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.support.PekaoTestPdf;
import com.financedashboard.web.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class StatementImportApiTest extends AbstractIntegrationTest {

    /** The canonical account number carried by {@link PekaoTestPdf#threeTransactions()}. */
    private static final String FIXTURE_ACCOUNT = "00000000000000000000000002";

    private long insertAccount(String currency, String accountNumber) {
        return jdbcTemplate.queryForObject("""
                insert into account (name, currency, account_number) values (?, ?, ?) returning id
                """, Long.class, "Account " + currency, currency, accountNumber);
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "wyciag.pdf", "application/pdf",
                PekaoTestPdf.threeTransactions());
    }

    @Test
    void importCreatesTheAccountFromTheStatementAndPersistsTransactions() throws Exception {
        mockMvc.perform(multipart("/api/v1/statements").file(pdfFile()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadyImported").value(false))
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.statementId").isNumber());

        Long accountId = jdbcTemplate.queryForObject(
                "select id from account where account_number = ?", Long.class, FIXTURE_ACCOUNT);
        assertThat(accountId).isNotNull();
        String currency = jdbcTemplate.queryForObject(
                "select currency from account where id = ?", String.class, accountId);
        assertThat(currency).isEqualTo("PLN");

        mockMvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void reimportingSameFileIsIdempotentAndDoesNotDuplicateTheAccount() throws Exception {
        MockMultipartFile sameFile = pdfFile();

        mockMvc.perform(multipart("/api/v1/statements").file(sameFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(3));

        mockMvc.perform(multipart("/api/v1/statements").file(sameFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadyImported").value(true))
                .andExpect(jsonPath("$.imported").value(0));

        Integer accounts = jdbcTemplate.queryForObject(
                "select count(*) from account where account_number = ?", Integer.class, FIXTURE_ACCOUNT);
        assertThat(accounts).isEqualTo(1);
        Long accountId = jdbcTemplate.queryForObject(
                "select id from account where account_number = ?", Long.class, FIXTURE_ACCOUNT);
        mockMvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(accountId)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void importReusesAnExistingAccountWithTheSameNumber() throws Exception {
        long existingId = insertAccount("PLN", FIXTURE_ACCOUNT);

        mockMvc.perform(multipart("/api/v1/statements").file(pdfFile()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(3));

        assertThat((Integer) jdbcTemplate.queryForObject(
                "select count(*) from account where id = ?", Integer.class, existingId))
                .isEqualTo(1);
        mockMvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(existingId)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void importOfANewAccountNumberAddsAsecondAccountOfTheSameCurrency() throws Exception {
        insertAccount("PLN", "111122223333444455556666");

        mockMvc.perform(multipart("/api/v1/statements").file(pdfFile()))
                .andExpect(status().isCreated());

        Integer plnAccounts = jdbcTemplate.queryForObject(
                "select count(*) from account where currency = 'PLN'", Integer.class);
        assertThat(plnAccounts).isEqualTo(2);
    }

    @Test
    void rejectsStatementWhoseAccountNumberPointsToADifferentCurrency() throws Exception {
        insertAccount("USD", FIXTURE_ACCOUNT);

        mockMvc.perform(multipart("/api/v1/statements").file(pdfFile()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnrecognizedDocument() throws Exception {
        MockMultipartFile bogus = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "%PDF-1.4\nnot a real statement".getBytes());

        mockMvc.perform(multipart("/api/v1/statements").file(bogus))
                .andExpect(status().isUnsupportedMediaType());
    }
}
