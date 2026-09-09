package com.financedashboard.web.controller;

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
                .andExpect(jsonPath("$[0].categoryId").value(categoryId));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/merchant-rules")).andReturn().getResponse().getContentAsString());

        mockMvc.perform(delete("/api/v1/merchant-rules/{id}", list.get(0).get("id").asLong()))
                .andExpect(status().isNoContent());

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
    void applyWithoutMatchesReturnsZero() throws Exception {
        long categoryId = insertCategory("Taxes");
        createRule("No Such Merchant", categoryId);

        mockMvc.perform(post("/api/v1/merchant-rules/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(0));
    }
}
