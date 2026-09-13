package com.financedashboard.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MerchantRuleApiTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private long insertCategory(String name) {
        return jdbcTemplate.queryForObject(
                "insert into category (name) values (?) returning id", Long.class, name);
    }

    private long insertTransaction(long statementId, long accountId, String merchant, long categoryId) {
        return jdbcTemplate.queryForObject("""
                insert into transaction (statement_id, account_id, transaction_date, amount,
                        currency, nature, description, merchant, category_id)
                values (?, ?, date '2026-03-05', -100.00, 'PLN', 'EXPENSE', 'op', ?, ?) returning id
                """, Long.class, statementId, accountId, merchant, categoryId);
    }

    private long createRule(String merchant, long categoryId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/merchant-rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"merchant":"%s","categoryId":%d}
                                """.formatted(merchant, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createListAndDeleteARule() throws Exception {
        long categoryId = insertCategory("Taxes");

        mockMvc.perform(post("/api/v1/merchant-rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"merchant":"  Example Merchant ","categoryId":%d}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchant").value("EXAMPLE MERCHANT"))
                .andExpect(jsonPath("$.categoryName").value("Taxes"));

        mockMvc.perform(get("/api/v1/merchant-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$[0].claimedRows").value(0));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/merchant-rules")).andReturn().getResponse().getContentAsString());

        mockMvc.perform(delete("/api/v1/merchant-rules/{id}", list.get(0).get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(get("/api/v1/merchant-rules"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateMerchantIsRejected() throws Exception {
        long categoryId = insertCategory("Taxes");
        String body = """
                {"merchant":"Example Merchant","categoryId":%d}
                """.formatted(categoryId);

        createRule("Example Merchant", categoryId);
        mockMvc.perform(post("/api/v1/merchant-rules").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsBlankMerchantAndUnknownCategory() throws Exception {
        mockMvc.perform(post("/api/v1/merchant-rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"merchant":"","categoryId":1}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/merchant-rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"merchant":"Some","categoryId":424242}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearAllDeletesRulesAndRevertsTransactionsThatUsedThem() throws Exception {
        long categoryId = insertCategory("Taxes");
        long otherId = insertCategory("Other");
        long accountId = jdbcTemplate.queryForObject(
                "insert into account (name, currency) values ('Personal PLN', 'PLN') returning id",
                Long.class);
        long statementId = jdbcTemplate.queryForObject(
                "insert into bank_statement (account_id, bank, file_hash) values (?, 'PEKAO', 'h-clear') returning id",
                Long.class, accountId);
        long used = insertTransaction(statementId, accountId, "Example Merchant", categoryId);
        long notUsed = insertTransaction(statementId, accountId, "Example Merchant", otherId);

        createRule("Example Merchant", categoryId);

        mockMvc.perform(delete("/api/v1/merchant-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesRemoved").value(1))
                .andExpect(jsonPath("$.transactionsUncategorized").value(1));

        String ruleCategory = jdbcTemplate.queryForObject(
                "select category_id from transaction where id = ?", String.class, used);
        String keptCategory = jdbcTemplate.queryForObject(
                "select category_id from transaction where id = ?", String.class, notUsed);
        assertThat(ruleCategory).isNull();
        assertThat(keptCategory).isEqualTo(String.valueOf(otherId));
        mockMvc.perform(get("/api/v1/merchant-rules"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listReportsTheRowsEachDefaultClaims() throws Exception {
        long categoryId = insertCategory("Taxes");
        long otherId = insertCategory("Other");
        long accountId = jdbcTemplate.queryForObject(
                "insert into account (name, currency) values ('Personal PLN', 'PLN') returning id",
                Long.class);
        long statementId = jdbcTemplate.queryForObject(
                "insert into bank_statement (account_id, bank, file_hash) values (?, 'PEKAO', 'h-claims') returning id",
                Long.class, accountId);
        insertTransaction(statementId, accountId, "Example Merchant", categoryId);
        insertTransaction(statementId, accountId, "Example Merchant", otherId);

        createRule("Example Merchant", categoryId);

        mockMvc.perform(get("/api/v1/merchant-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimedRows").value(1));
    }

    @Test
    void deleteRevertsTaggedTransactionsAndReportsTheCount() throws Exception {
        long categoryId = insertCategory("Taxes");
        long otherId = insertCategory("Other");
        long accountId = jdbcTemplate.queryForObject(
                "insert into account (name, currency) values ('Personal PLN', 'PLN') returning id",
                Long.class);
        long statementId = jdbcTemplate.queryForObject(
                "insert into bank_statement (account_id, bank, file_hash) values (?, 'PEKAO', 'h-delete') returning id",
                Long.class, accountId);
        long used = insertTransaction(statementId, accountId, "Example Merchant", categoryId);
        long otherCategory = insertTransaction(statementId, accountId, "Example Merchant", otherId);

        long ruleId = createRule("Example Merchant", categoryId);

        mockMvc.perform(delete("/api/v1/merchant-rules/{id}", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "select category_id from transaction where id = ?", String.class, used)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select category_id from transaction where id = ?", String.class, otherCategory))
                .isEqualTo(String.valueOf(otherId));
        mockMvc.perform(get("/api/v1/merchant-rules"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unlinkRevertsTaggedTransactionsAndKeepsTheDefault() throws Exception {
        long categoryId = insertCategory("Taxes");
        long otherId = insertCategory("Other");
        long accountId = jdbcTemplate.queryForObject(
                "insert into account (name, currency) values ('Personal PLN', 'PLN') returning id",
                Long.class);
        long statementId = jdbcTemplate.queryForObject(
                "insert into bank_statement (account_id, bank, file_hash) values (?, 'PEKAO', 'h-unlink') returning id",
                Long.class, accountId);
        long used = insertTransaction(statementId, accountId, "Example Merchant", categoryId);
        long otherCategory = insertTransaction(statementId, accountId, "Example Merchant", otherId);

        long ruleId = createRule("Example Merchant", categoryId);

        mockMvc.perform(post("/api/v1/merchant-rules/{id}/unlink", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "select category_id from transaction where id = ?", String.class, used)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select category_id from transaction where id = ?", String.class, otherCategory))
                .isEqualTo(String.valueOf(otherId));
        mockMvc.perform(get("/api/v1/merchant-rules"))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/v1/merchant-rules/{id}/apply", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1));
    }

    @Test
    void unlinkUnknownDefaultReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/merchant-rules/999999/unlink"))
                .andExpect(status().isNotFound());
    }

    @Test
    void applyWithoutMatchesReturnsZero() throws Exception {
        long categoryId = insertCategory("Taxes");
        long ruleId = createRule("No Such Merchant", categoryId);

        mockMvc.perform(post("/api/v1/merchant-rules/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(0));

        mockMvc.perform(post("/api/v1/merchant-rules/{id}/apply", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(0));

        mockMvc.perform(post("/api/v1/merchant-rules/999999/apply"))
                .andExpect(status().isNotFound());
    }
}
