package com.financedashboard.web.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionApiTest extends AbstractIntegrationTest {

    private Long txA;
    private Long txB;
    private Long txC;
    private Long txD;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
                insert into account (id, name, currency) values (1, 'Personal', 'PLN')
                """);
        jdbcTemplate.update("""
                insert into bank_statement (id, account_id, bank, file_hash)
                values (1, 1, 'TEST', 'seed-hash')
                """);
        jdbcTemplate.update("""
                insert into category (id, name, color, sort_order) values (100, 'Food', '#fff', 10)
                """);
        jdbcTemplate.update("""
                insert into category (id, name, color, sort_order) values (101, 'Fun', '#000', 20)
                """);

        txA = insertTransaction(LocalDate.of(2026, 9, 1), "-100.00", "EXPENSE",
                "Groceries run", "Example Market", 100L);
        txB = insertTransaction(LocalDate.of(2026, 9, 5), "-40.00", "EXPENSE",
                "Pizza dinner", "Pizza Hut", 101L);
        txC = insertTransaction(LocalDate.of(2026, 9, 10), "2000.00", "INCOME",
                "Monthly salary", "Employer", null);
        txD = insertTransaction(LocalDate.of(2026, 9, 15), "-300.00", "EXPENSE",
                "Hotel night", "Grand Hotel", null);
    }

    private Long insertTransaction(LocalDate date, String amount, String nature,
                                   String description, String merchant, Long categoryId) {
        return insertTransaction(date, amount, "PLN", nature, description, merchant, categoryId);
    }

    private Long insertTransaction(LocalDate date, String amount, String currency, String nature,
                                   String description, String merchant, Long categoryId) {
        Long id = jdbcTemplate.queryForObject("""
                insert into transaction
                    (statement_id, account_id, transaction_date, amount, currency, nature,
                     description, merchant, category_id)
                values (1, 1, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                Date.valueOf(date), new BigDecimal(amount), currency, nature, description, merchant, categoryId);
        insertStoredAmount(id, currency, amount);
        return id;
    }

    private void insertStoredAmount(Long transactionId, String currency, String amount) {
        jdbcTemplate.update("""
                insert into transaction_amount (transaction_id, currency, amount) values (?, ?, ?)
                """, transactionId, currency, new BigDecimal(amount));
    }

    @Test
    void listsTransactionsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].description").value("Hotel night"))
                .andExpect(jsonPath("$.content[3].description").value("Groceries run"));
    }

    @Test
    void filtersByDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("from", "2026-09-03")
                        .param("to", "2026-09-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].description").value("Monthly salary"))
                .andExpect(jsonPath("$.content[1].description").value("Pizza dinner"));
    }

    @Test
    void filtersByNature() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("nature", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].amount").value(closeTo(-300.0, 0.001)));
    }

    @Test
    void filtersByCategoryAndUncategorized() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("categoryId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Groceries run"));

        mockMvc.perform(get("/api/v1/transactions").param("uncategorized", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtersByAccount() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("accountId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByMerchantAndByHavingNone() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("merchant", "Example Market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(txA));

        mockMvc.perform(get("/api/v1/transactions").param("withoutMerchant", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        jdbcTemplate.update("update transaction set merchant = '  Example Market  ' where id = ?", txB);
        jdbcTemplate.update("update transaction set merchant = null where id = ?", txD);

        mockMvc.perform(get("/api/v1/transactions").param("merchant", "Example Market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/transactions").param("withoutMerchant", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(txD));
    }

    @Test
    void listsOnlyTheGivenIds() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("ids", String.valueOf(txA))
                        .param("ids", String.valueOf(txD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(txD))
                .andExpect(jsonPath("$.content[1].id").value(txA));

        mockMvc.perform(get("/api/v1/transactions").param("ids", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void combinesTheIdFilterWithTheOtherFilters() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("ids", String.valueOf(txA))
                        .param("ids", String.valueOf(txB))
                        .param("categoryId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(txB));
    }

    @Test
    void searchesByDescriptionAndMerchant() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("q", "pizza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(txB));

        mockMvc.perform(get("/api/v1/transactions").param("q", "Salary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(txC));
    }

    @Test
    void pagesResults() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].description").value("Hotel night"));

        mockMvc.perform(get("/api/v1/transactions").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Pizza dinner"));
    }

    @Test
    void sortsByAmountAscending() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("sort", "amount").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].description").value("Hotel night"))
                .andExpect(jsonPath("$.content[1].description").value("Groceries run"))
                .andExpect(jsonPath("$.content[3].description").value("Monthly salary"));
    }

    @Test
    void sortsByAmountByTheWorthInTheBaseCurrency() throws Exception {
        Long usd = insertTransaction(LocalDate.of(2026, 9, 3), "-1000.00", "USD", "EXPENSE",
                "Conference fee", "Example Payee", null);
        insertStoredAmount(usd, "PLN", "-4000.00");

        mockMvc.perform(get("/api/v1/transactions").param("sort", "amount").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].id").value(usd))
                .andExpect(jsonPath("$.content[0].amount").value(closeTo(-1000.0, 0.001)))
                .andExpect(jsonPath("$.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.content[1].description").value("Hotel night"))
                .andExpect(jsonPath("$.content[4].description").value("Monthly salary"));

        mockMvc.perform(get("/api/v1/transactions").param("sort", "amount").param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Monthly salary"))
                .andExpect(jsonPath("$.content[4].id").value(usd));
    }

    @Test
    void keepsRowsWithoutABaseCurrencyValueAndRanksThemLast() throws Exception {
        Long unvalued = jdbcTemplate.queryForObject("""
                insert into transaction
                    (statement_id, account_id, transaction_date, amount, currency, nature, description)
                values (1, 1, '2026-09-03', '-5000.00', 'USD', 'EXPENSE', 'Unpriced subscription')
                returning id
                """, Long.class);

        mockMvc.perform(get("/api/v1/transactions").param("sort", "amount").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].description").value("Hotel night"))
                .andExpect(jsonPath("$.content[4].id").value(unvalued));

        mockMvc.perform(get("/api/v1/transactions").param("sort", "amount").param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Monthly salary"))
                .andExpect(jsonPath("$.content[4].id").value(unvalued));
    }

    @Test
    void pagesAmountSortedResultsByTheirBaseCurrencyWorth() throws Exception {
        Long usd = insertTransaction(LocalDate.of(2026, 9, 3), "-1000.00", "USD", "EXPENSE",
                "Conference fee", "Example Payee", null);
        insertStoredAmount(usd, "PLN", "-4000.00");

        mockMvc.perform(get("/api/v1/transactions")
                        .param("sort", "amount")
                        .param("order", "asc")
                        .param("nature", "EXPENSE")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].description").value("Groceries run"))
                .andExpect(jsonPath("$.content[1].description").value("Pizza dinner"));
    }

    @Test
    void sortsByCategoryNameKeepingUncategorizedRows() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("sort", "category").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].description").value("Groceries run"))
                .andExpect(jsonPath("$.content[1].description").value("Pizza dinner"))
                .andExpect(jsonPath("$.content[2].description").value("Hotel night"))
                .andExpect(jsonPath("$.content[3].description").value("Monthly salary"));
    }

    @Test
    void sortsByAccountName() throws Exception {
        jdbcTemplate.update("""
                insert into account (id, name, currency) values (2, 'Business PLN', 'PLN')
                """);
        jdbcTemplate.update("""
                insert into transaction (statement_id, account_id, transaction_date, amount,
                    currency, nature, description)
                values (1, 2, '2026-09-02', '-5.00', 'PLN', 'EXPENSE', 'Business coffee')
                """);
        mockMvc.perform(get("/api/v1/transactions").param("sort", "account").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].description").value("Business coffee"));
    }

    @Test
    void getsSingleTransaction() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", txA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(txA))
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.statementId").value(1))
                .andExpect(jsonPath("$.nature").value("EXPENSE"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.categoryId").value(100))
                .andExpect(jsonPath("$.amount").value(closeTo(-100.0, 0.001)));
    }

    @Test
    void getMissingTransactionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/424242"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidNatureParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("nature", "WAT"))
                .andExpect(status().isBadRequest());
    }
}
