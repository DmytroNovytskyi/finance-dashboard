package com.financedashboard.web.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CategoryApiTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private long createCategory(String name, String color) throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","color":"%s"}
                                """.formatted(name, color)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createAndListCategories() throws Exception {
        createCategory("Groceries", "#4CAF50");
        createCategory("Transport", "#2196F3");

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateCategoryRenamesAndRecolors() throws Exception {
        long id = createCategory("Old", "#000000");

        mockMvc.perform(patch("/api/v1/categories/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"New","color":"#FFFFFF"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.color").value("#FFFFFF"));
    }

    @Test
    void uncategorizeClearsItsTransactionsButKeepsTheCategory() throws Exception {
        long categoryId = createCategory("Taxes", "#000000");
        long accountId = jdbcTemplate.queryForObject(
                "insert into account (name, currency) values ('Personal PLN', 'PLN') returning id",
                Long.class);
        long statementId = jdbcTemplate.queryForObject(
                "insert into bank_statement (account_id, bank, file_hash) values (?, 'PEKAO', 'h-uncat') returning id",
                Long.class, accountId);
        long txId = jdbcTemplate.queryForObject("""
                insert into transaction (statement_id, account_id, transaction_date, amount,
                        currency, nature, description, category_id)
                values (?, ?, date '2026-03-05', -10.00, 'PLN', 'EXPENSE', 'op', ?) returning id
                """, Long.class, statementId, accountId, categoryId);

        mockMvc.perform(post("/api/v1/categories/{id}/uncategorize", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isOk());

        Integer assigned = jdbcTemplate.queryForObject(
                "select count(*) from transaction where id = ? and category_id is null", Integer.class, txId);
        org.assertj.core.api.Assertions.assertThat(assigned).isEqualTo(1);
    }

    @Test
    void deleteCategoryRemovesIt() throws Exception {
        long id = createCategory("Dining", "#FF9800");

        mockMvc.perform(delete("/api/v1/categories/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/categories/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMissingCategoryReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/424242"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCategoryRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAfterSeededExplicitIdsUsesFreshId() throws Exception {
        // Reproduces the V1 seeding scenario on a real DB: rows carry explicit ids 1..3 and the
        // identity sequence is synced to 3 (as V2 does). A create must then get id 4, not reuse a
        // colliding low id.
        jdbcTemplate.update(
                "insert into category (id, name, color, sort_order) values (1, 'Seed A', null, 0),"
                        + " (2, 'Seed B', null, 0), (3, 'Seed C', null, 0)");
        jdbcTemplate.queryForObject(
                "select setval(pg_get_serial_sequence('category', 'id'), greatest((select max(id) from category), 1))",
                Long.class);

        String body = mockMvc.perform(post("/api/v1/categories")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Fresh"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(body).get("id").asLong())
                .isGreaterThan(3L);
    }
}
