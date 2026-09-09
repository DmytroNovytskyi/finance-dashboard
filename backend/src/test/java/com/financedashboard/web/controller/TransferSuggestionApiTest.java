package com.financedashboard.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransferSuggestionApiTest extends AbstractIntegrationTest {

    private static final String ACCOUNT_A = "00000000000000000000000003";
    private static final String ACCOUNT_B = "00000000000000000000000004";

    @Test
    void suggestsAndAppliesOwnAccountPairs() throws Exception {
        long accountA = insertAccount(ACCOUNT_A, "USD");
        long accountB = insertAccount(ACCOUNT_B, "USD");
        long statementA = insertStatement(accountA, "hA");
        long statementB = insertStatement(accountB, "hB");

        // A -> B outbound referencing B, and B's inbound referencing A (mirror)
        insertTransaction(statementA, accountA, "-100.00", "USD",
                "PRZELEW MOBILE ... " + ACCOUNT_B);
        insertTransaction(statementB, accountB, "100.00", "USD",
                "PRZELEW MOBILE ... SACC " + ACCOUNT_A);

        mockMvc.perform(get("/api/v1/transfers/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(100.0))
                .andExpect(jsonPath("$[0].currency").value("USD"));

        mockMvc.perform(post("/api/v1/transfers/suggestions/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1));

        mockMvc.perform(get("/api/v1/transfers/suggestions"))
                .andExpect(jsonPath("$.length()").value(0));

        String natureA = jdbcTemplate.queryForObject(
                "select nature from transaction where account_id = ? and amount < 0", String.class, accountA);
        String natureB = jdbcTemplate.queryForObject(
                "select nature from transaction where account_id = ? and amount > 0", String.class, accountB);
        assertThat(natureA).isEqualTo("TRANSFER");
        assertThat(natureB).isEqualTo("TRANSFER");
    }

    @Test
    void unlinkRevertsAnAppliedTransferBackToASuggestion() throws Exception {
        long accountA = insertAccount(ACCOUNT_A, "USD");
        long accountB = insertAccount(ACCOUNT_B, "USD");
        long statementA = insertStatement(accountA, "huA");
        long statementB = insertStatement(accountB, "huB");
        insertTransaction(statementA, accountA, "-100.00", "USD",
                "PRZELEW MOBILE ... " + ACCOUNT_B);
        insertTransaction(statementB, accountB, "100.00", "USD",
                "PRZELEW MOBILE ... SACC " + ACCOUNT_A);

        mockMvc.perform(post("/api/v1/transfers/suggestions/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1));

        Long outboundId = jdbcTemplate.queryForObject(
                "select id from transaction where account_id = ? and amount < 0", Long.class, accountA);
        mockMvc.perform(post("/api/v1/transfers/" + outboundId + "/unlink"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        String natureA = jdbcTemplate.queryForObject(
                "select nature from transaction where id = ?", String.class, outboundId);
        Long inboundId = jdbcTemplate.queryForObject(
                "select id from transaction where account_id = ? and amount > 0", Long.class, accountB);
        String natureB = jdbcTemplate.queryForObject(
                "select nature from transaction where id = ?", String.class, inboundId);
        assertThat(natureA).isEqualTo("EXPENSE");
        assertThat(natureB).isEqualTo("INCOME");

        mockMvc.perform(get("/api/v1/transfers/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private long insertAccount(String accountNumber, String currency) {
        return jdbcTemplate.queryForObject("""
                insert into account (name, currency, account_number) values (?, ?, ?) returning id
                """, Long.class, "Account", currency, accountNumber);
    }

    private long insertStatement(long accountId, String hash) {
        return jdbcTemplate.queryForObject("""
                insert into bank_statement (account_id, bank, file_hash) values (?, 'TEST', ?) returning id
                """, Long.class, accountId, hash);
    }

    private void insertTransaction(long statementId, long accountId, String amount,
                                   String currency, String description) {
        BigDecimal value = new BigDecimal(amount);
        String nature = value.signum() < 0 ? "EXPENSE" : "INCOME";
        jdbcTemplate.update("""
                insert into transaction (statement_id, account_id, transaction_date, amount, currency,
                    nature, description)
                values (?, ?, ?, ?, ?, ?, ?)
                """, statementId, accountId, Date.valueOf(LocalDate.of(2026, 3, 10)),
                value, currency, nature, description);
    }
}
