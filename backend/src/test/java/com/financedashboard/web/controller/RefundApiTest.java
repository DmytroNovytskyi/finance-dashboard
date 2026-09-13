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
                .andExpect(jsonPath("$[0].refundTransactionIds[0]").value(refundId))
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
    void linksSeveralLegsIntoOneRefundAndUnlinksAllOfThem() throws Exception {
        long categoryId = insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long purchaseId = insertTransaction(statementId, accountId, "-61.46", "EXPENSE",
                "TRANSAKCJA KARTĄ PŁATNICZĄ example-shop Somewhere", "example-shop");
        long firstId = insertTransaction(statementId, accountId, "14.45", "INCOME", REVERSAL, null);
        long secondId = insertTransaction(statementId, accountId, "23.27", "INCOME", REVERSAL, null);
        long thirdId = insertTransaction(statementId, accountId, "23.74", "INCOME", REVERSAL, null);

        mockMvc.perform(get("/api/v1/refunds/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].purchaseTransactionId").value(purchaseId))
                .andExpect(jsonPath("$[0].refundTransactionIds.length()").value(3))
                .andExpect(jsonPath("$[0].amount").value(61.46))
                .andExpect(jsonPath("$[0].reason").value("ANCHORED"));

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[" + purchaseId + "," + firstId + ","
                                + secondId + "," + thirdId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactions.length()").value(4))
                .andExpect(jsonPath("$.transactions[0].nature").value("REFUND"));

        for (long id : new long[] {purchaseId, firstId, secondId, thirdId}) {
            assertThat(natureOf(id)).isEqualTo("REFUND");
            assertThat(categoryOf(id)).isEqualTo(categoryId);
        }

        mockMvc.perform(post("/api/v1/refunds/" + secondId + "/unlink"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));

        assertThat(natureOf(purchaseId)).isEqualTo("EXPENSE");
        assertThat(natureOf(firstId)).isEqualTo("INCOME");
        assertThat(categoryOf(thirdId)).isNull();
    }

    @Test
    void linksAGroupWhoseSidesDoNotBalance() throws Exception {
        long categoryId = insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long firstOut = insertTransaction(statementId, accountId, "-1000.00", "EXPENSE", "BLIK WYCHODZĄCY", null);
        long secondOut = insertTransaction(statementId, accountId, "-1000.00", "EXPENSE", "BLIK WYCHODZĄCY", null);
        long firstIn = insertTransaction(statementId, accountId, "2500.00", "INCOME", "BLIK PRZYCHODZĄCY", null);
        long secondIn = insertTransaction(statementId, accountId, "500.00", "INCOME", "BLIK PRZYCHODZĄCY", null);

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[" + firstOut + "," + secondOut + ","
                                + firstIn + "," + secondIn + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundGroupId").isNotEmpty())
                .andExpect(jsonPath("$.transactions.length()").value(4));

        for (long id : new long[] {firstOut, secondOut, firstIn, secondIn}) {
            assertThat(natureOf(id)).isEqualTo("REFUND");
            assertThat(categoryOf(id)).isEqualTo(categoryId);
        }
    }

    @Test
    void linksAGroupThatOnlyGoesOneWay() throws Exception {
        insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long firstId = insertTransaction(statementId, accountId, "-50.00", "EXPENSE", "SOME SHOP", null);
        long secondId = insertTransaction(statementId, accountId, "-20.00", "EXPENSE", "OTHER SHOP", null);

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[" + firstId + "," + secondId + "]}"))
                .andExpect(status().isCreated());

        assertThat(refundGroupOf(firstId)).isEqualTo(refundGroupOf(secondId)).isNotNull();
    }

    @Test
    void rejectsASingleLeg() throws Exception {
        insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long onlyId = insertTransaction(statementId, accountId, "-50.00", "EXPENSE", "SOME SHOP", null);

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[" + onlyId + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsALegNamedTwice() throws Exception {
        insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long onlyId = insertTransaction(statementId, accountId, "-50.00", "EXPENSE", "SOME SHOP", null);

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[" + onlyId + "," + onlyId + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsALegThatIsAlreadyLinked() throws Exception {
        insertReservedCategory();
        long accountId = insertAccount();
        long statementId = insertStatement(accountId);
        long firstId = insertTransaction(statementId, accountId, "-50.00", "EXPENSE", "SOME SHOP", null);
        long secondId = insertTransaction(statementId, accountId, "50.00", "INCOME", "ZWROT TRANSAKCJI", null);
        long thirdId = insertTransaction(statementId, accountId, "-10.00", "EXPENSE", "OTHER SHOP", null);

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[" + firstId + "," + secondId + "]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[" + firstId + "," + thirdId + "]}"))
                .andExpect(status().isBadRequest());

        assertThat(refundGroupOf(thirdId)).isNull();
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
