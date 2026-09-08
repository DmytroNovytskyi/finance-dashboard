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
}
