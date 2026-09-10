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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountApiTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAccountNormalizesAndPersists() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"  Personal PLN ","currency":"pln","kind":"PERSONAL","accountNumber":"123456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Personal PLN"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.kind").value("PERSONAL"))
                .andExpect(jsonPath("$.accountNumber").value("123456"));
    }

    @Test
    void createAccountRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"  ","currency":"PLN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAccountRejectsInvalidCurrency() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Account","currency":"PL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAndUpdateAccount() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Personal PLN","currency":"PLN"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Personal PLN"));

        mockMvc.perform(patch("/api/v1/accounts/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed","kind":"BUSINESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.kind").value("BUSINESS"))
                .andExpect(jsonPath("$.currency").value("PLN"));
    }

    @Test
    void updateCannotChangeTheCurrencyOrTheAccountNumber() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Personal PLN","currency":"PLN","accountNumber":"123456"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(patch("/api/v1/accounts/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed","currency":"USD","accountNumber":"999999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.accountNumber").value("123456"));
    }

    @Test
    void updateClearsTheKindWhenNoneIsGiven() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Personal PLN","currency":"PLN","kind":"PERSONAL"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(patch("/api/v1/accounts/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Personal PLN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").doesNotExist());
    }

    @Test
    void updateRejectsABlankName() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Personal PLN","currency":"PLN"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(patch("/api/v1/accounts/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesTheAccountItsStatementsAndTransactions() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Personal PLN","currency":"PLN"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();
        long statementId = jdbcTemplate.queryForObject(
                "insert into bank_statement (account_id, bank, file_hash) values (?, 'PEKAO', 'h-del') returning id",
                Long.class, id);
        jdbcTemplate.update("""
                insert into transaction (statement_id, account_id, transaction_date, amount,
                        currency, nature, description)
                values (?, ?, date '2026-03-05', -10.00, 'PLN', 'EXPENSE', 'op')
                """, statementId, id);

        mockMvc.perform(delete("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        assertThat((Integer) jdbcTemplate.queryForObject(
                "select count(*) from account where id = ?", Integer.class, id)).isZero();
        assertThat((Integer) jdbcTemplate.queryForObject(
                "select count(*) from bank_statement where id = ?", Integer.class, statementId)).isZero();
        assertThat((Integer) jdbcTemplate.queryForObject(
                "select count(*) from transaction where account_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void deleteMissingAccountReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/424242"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMissingAccountReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void persistenceSurvivesSave() throws Exception {
        JsonNode node = objectMapper.readTree(mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Business USD","currency":"usd","kind":"BUSINESS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        long id = node.get("id").asLong();

        String fetched = mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(fetched).contains("\"name\":\"Business USD\"");
        assertThat(fetched).contains("\"currency\":\"USD\"");
    }
}
