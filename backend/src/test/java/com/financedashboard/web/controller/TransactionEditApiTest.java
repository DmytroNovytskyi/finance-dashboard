package com.financedashboard.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionEditApiTest extends AbstractIntegrationTest {

    private long createAccount(String currency) {
        return jdbcTemplate.queryForObject("""
                insert into account (name, currency) values (?, ?) returning id
                """, Long.class, "Account " + currency, currency);
    }

    private long createStatement(long accountId, String hash) {
        return jdbcTemplate.queryForObject("""
                insert into bank_statement (account_id, bank, file_hash) values (?, 'TEST', ?) returning id
                """, Long.class, accountId, hash);
    }

    private long createCategory(String name) {
        return jdbcTemplate.queryForObject("""
                insert into category (name) values (?) returning id
                """, Long.class, name);
    }

    private long insertTransaction(long statementId, long accountId, LocalDate date,
                                   String amount, String currency, String nature, String description) {
        return jdbcTemplate.queryForObject("""
                insert into transaction (statement_id, account_id, transaction_date, amount, currency,
                    nature, description)
                values (?, ?, ?, ?, ?, ?, ?) returning id
                """, Long.class, statementId, accountId, Date.valueOf(date),
                new BigDecimal(amount), currency, nature, description);
    }

    @Test
    void patchAssignsAndClearsCategory() throws Exception {
        long accountId = createAccount("PLN");
        long statementId = createStatement(accountId, "h1");
        long tx = insertTransaction(statementId, accountId, LocalDate.of(2026, 8, 1),
                "-100.00", "PLN", "EXPENSE", "Groceries");
        long categoryId = createCategory("Food");

        mockMvc.perform(patch("/api/v1/transactions/{id}", tx)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d}
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId));

        mockMvc.perform(patch("/api/v1/transactions/{id}", tx)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"categoryId": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    @Test
    void patchRejectsUnknownCategoryAndTransferNature() throws Exception {
        long accountId = createAccount("PLN");
        long statementId = createStatement(accountId, "h2");
        long tx = insertTransaction(statementId, accountId, LocalDate.of(2026, 8, 2),
                "-10.00", "PLN", "EXPENSE", "Bus");

        mockMvc.perform(patch("/api/v1/transactions/{id}", tx)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"categoryId": 999}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/transactions/{id}", tx)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nature": "TRANSFER"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uncategorizeAllClearsEveryCategorizedRow() throws Exception {
        long accountId = createAccount("PLN");
        long statementId = createStatement(accountId, "h-uncat");
        long tx = insertTransaction(statementId, accountId, LocalDate.of(2026, 8, 5),
                "-12.00", "PLN", "EXPENSE", "Bus");
        long categoryId = createCategory("Food");
        mockMvc.perform(patch("/api/v1/transactions/{id}", tx)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d}
                                """.formatted(categoryId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/transactions/uncategorize-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/v1/transactions").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/transactions").param("uncategorized", "true"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void bulkAssignsAndClearsCategory() throws Exception {
        long accountId = createAccount("PLN");
        long statementId = createStatement(accountId, "h3");
        long tx1 = insertTransaction(statementId, accountId, LocalDate.of(2026, 8, 3),
                "-1.00", "PLN", "EXPENSE", "A");
        long tx2 = insertTransaction(statementId, accountId, LocalDate.of(2026, 8, 4),
                "-2.00", "PLN", "EXPENSE", "B");
        long categoryId = createCategory("Fun");

        mockMvc.perform(post("/api/v1/transactions/categorize")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"transactionIds": [%d, %d], "categoryId": %d}
                                """.formatted(tx1, tx2, categoryId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(post("/api/v1/transactions/categorize")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"transactionIds": [%d, %d], "categoryId": null}
                                """.formatted(tx1, tx2)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions").param("uncategorized", "true"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void pairsTwoOwnAccountTransactionsAsTransfer() throws Exception {
        long accountA = createAccount("PLN");
        long accountB = createAccount("USD");
        long statementA = createStatement(accountA, "h4");
        long statementB = createStatement(accountB, "h5");
        long legA = insertTransaction(statementA, accountA, LocalDate.of(2026, 8, 5),
                "-100.00", "PLN", "EXPENSE", "To USD");
        long legB = insertTransaction(statementB, accountB, LocalDate.of(2026, 8, 5),
                "80.00", "USD", "INCOME", "From PLN");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fromTransactionId": %d, "toTransactionId": %d}
                                """.formatted(legA, legB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.from.nature").value("TRANSFER"))
                .andExpect(jsonPath("$.to.nature").value("TRANSFER"))
                .andExpect(jsonPath("$.transferGroupId").isString());

        String groupA = jdbcTemplate.queryForObject(
                "select transfer_group_id from transaction where id = ?", String.class, legA);
        String groupB = jdbcTemplate.queryForObject(
                "select transfer_group_id from transaction where id = ?", String.class, legB);
        assertThat(groupA).isNotBlank().isEqualTo(groupB);

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fromTransactionId": %d, "toTransactionId": %d}
                                """.formatted(legA, legB)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTransferWithinTheSameAccount() throws Exception {
        long accountA = createAccount("PLN");
        long statement = createStatement(accountA, "h6");
        long leg1 = insertTransaction(statement, accountA, LocalDate.of(2026, 8, 6),
                "-100.00", "PLN", "EXPENSE", "x");
        long leg2 = insertTransaction(statement, accountA, LocalDate.of(2026, 8, 6),
                "100.00", "PLN", "INCOME", "y");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fromTransactionId": %d, "toTransactionId": %d}
                                """.formatted(leg1, leg2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingRangeRemovesEmptyStatementSoFileCanReimport() throws Exception {
        long accountId = createAccount("PLN");
        long statementId = createStatement(accountId, "unique-hash-7");
        insertTransaction(statementId, accountId, LocalDate.of(2026, 8, 10),
                "-5.00", "PLN", "EXPENSE", "Solo");

        mockMvc.perform(delete("/api/v1/transactions")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("accountId", String.valueOf(accountId)))
                .andExpect(status().isNoContent());

        Integer statements = jdbcTemplate.queryForObject(
                "select count(*) from bank_statement where id = ?", Integer.class, statementId);
        assertThat(statements).isZero();
    }

    @Test
    void deletingOneTransferLegUnpairsTheSurvivor() throws Exception {
        long accountA = createAccount("PLN");
        long accountB = createAccount("USD");
        long statementA = createStatement(accountA, "h8");
        long statementB = createStatement(accountB, "h9");
        long legA = insertTransaction(statementA, accountA, LocalDate.of(2026, 8, 20),
                "-100.00", "PLN", "EXPENSE", "to USD");
        long legB = insertTransaction(statementB, accountB, LocalDate.of(2026, 8, 20),
                "80.00", "USD", "INCOME", "from PLN");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fromTransactionId": %d, "toTransactionId": %d}
                                """.formatted(legA, legB)))
                .andExpect(status().isCreated());

        // delete only account A's leg (the outbound one)
        mockMvc.perform(delete("/api/v1/transactions")
                        .param("from", "2026-08-20")
                        .param("to", "2026-08-20")
                        .param("accountId", String.valueOf(accountA)))
                .andExpect(status().isNoContent());

        String natureB = jdbcTemplate.queryForObject(
                "select nature from transaction where id = ?", String.class, legB);
        String groupB = jdbcTemplate.queryForObject(
                "select transfer_group_id from transaction where id = ?", String.class, legB);
        assertThat(natureB).isEqualTo("INCOME");
        assertThat(groupB).isNull();
    }
}
