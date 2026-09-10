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
import org.springframework.http.MediaType;

class RefundApiTest extends AbstractIntegrationTest {

    private static final String ACCOUNT = "00000000000000000000000003";
    private static final String REVERSAL = "PRZELEW Bank Pekao S.A. ANULOWANIE TRANSAKCJI NA KARTĘ "
            + "**** **** 1111 2222 WYKONANEJ: example-shop \\Somewhere    DN. 05/03/2026";

    @Test
    void suggestsAppliesAndUnlinksAPurchaseAndItsRefund() throws Exception {
        long categoryId = insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long purchaseId = insertTransaction(statementId, accountId, "-12.34", "EXPENSE",
                "TRANSAKCJA KARTĄ PŁATNICZĄ example-shop Somewhere", "example-shop");
        long refundId = insertTransaction(statementId, accountId, "12.34", "INCOME", REVERSAL, null);

        mockMvc.perform(get("/api/v1/refunds/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].purchaseTransactionId").value(purchaseId))
                .andExpect(jsonPath("$[0].refundTransactionId").value(refundId))
                .andExpect(jsonPath("$[0].amount").value(12.34))
                .andExpect(jsonPath("$[0].reason").value("ANCHORED"));

        mockMvc.perform(post("/api/v1/refunds/suggestions/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1));

        assertThat(natureOf(purchaseId)).isEqualTo("REFUND");
        assertThat(natureOf(refundId)).isEqualTo("REFUND");
        assertThat(categoryOf(purchaseId)).isEqualTo(categoryId);
        assertThat(categoryOf(refundId)).isEqualTo(categoryId);

        mockMvc.perform(get("/api/v1/refunds/suggestions"))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/v1/refunds/" + refundId + "/unlink"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        assertThat(natureOf(purchaseId)).isEqualTo("EXPENSE");
        assertThat(natureOf(refundId)).isEqualTo("INCOME");
        assertThat(categoryOf(refundId)).isNull();
    }

    @Test
    void linksAPairChosenByHand() throws Exception {
        insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long purchaseId = insertTransaction(statementId, accountId, "-50.00", "EXPENSE", "SOME SHOP", null);
        long refundId = insertTransaction(statementId, accountId, "50.00", "INCOME", "ZWROT TRANSAKCJI", null);

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purchaseTransactionId\":" + purchaseId
                                + ",\"refundTransactionId\":" + refundId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundGroupId").isNotEmpty())
                .andExpect(jsonPath("$.purchase.nature").value("REFUND"))
                .andExpect(jsonPath("$.refund.nature").value("REFUND"));

        assertThat(refundGroupOf(purchaseId)).isEqualTo(refundGroupOf(refundId)).isNotNull();
    }

    @Test
    void rejectsAPairWhoseLegsHaveDifferentMagnitudes() throws Exception {
        insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long purchaseId = insertTransaction(statementId, accountId, "-50.00", "EXPENSE", "SOME SHOP", null);
        long refundId = insertTransaction(statementId, accountId, "20.00", "INCOME", "ZWROT TRANSAKCJI", null);

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purchaseTransactionId\":" + purchaseId
                                + ",\"refundTransactionId\":" + refundId + "}"))
                .andExpect(status().isBadRequest());
    }

    private long insertReservedCategory() {
        return jdbcTemplate.queryForObject("""
                insert into category (name, color, sort_order, system, system_key)
                values ('Refund', '#8D6E63', 1001, true, 'REFUND') returning id
                """, Long.class);
    }

    private long insertAccount() {
        return jdbcTemplate.queryForObject("""
                insert into account (name, currency, account_number) values (?, ?, ?) returning id
                """, Long.class, "Account", "PLN", ACCOUNT);
    }

    private long insertStatement(long accountId) {
        return jdbcTemplate.queryForObject("""
                insert into bank_statement (account_id, bank, file_hash) values (?, 'TEST', ?) returning id
                """, Long.class, accountId, "refund-hash");
    }

    private long insertTransaction(long statementId, long accountId, String amount, String nature,
                                   String description, String merchant) {
        return jdbcTemplate.queryForObject("""
                insert into transaction (statement_id, account_id, transaction_date, amount, currency,
                    nature, description, merchant)
                values (?, ?, ?, ?, 'PLN', ?, ?, ?) returning id
                """, Long.class, statementId, accountId, Date.valueOf(LocalDate.of(2026, 3, 5)),
                new BigDecimal(amount), nature, description, merchant);
    }

    private String natureOf(long id) {
        return jdbcTemplate.queryForObject("select nature from transaction where id = ?", String.class, id);
    }

    private Long categoryOf(long id) {
        return jdbcTemplate.queryForObject("select category_id from transaction where id = ?", Long.class, id);
    }

    private Object refundGroupOf(long id) {
        return jdbcTemplate.queryForObject("select refund_group_id from transaction where id = ?", Object.class, id);
    }
}
