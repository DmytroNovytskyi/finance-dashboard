package com.financedashboard.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StatisticsApiTest extends AbstractIntegrationTest {

    @Test
    void summarizesMonthCategoryAndMerchantTotals() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "h1");
        long groceries = insertCategory("Groceries");
        insertTx(statement, account, "2026-03-10", "-100", "EXPENSE", groceries, "Example Store");
        insertTx(statement, account, "2026-03-12", "-50", "EXPENSE", groceries, "Example Store");
        insertTx(statement, account, "2026-03-20", "-20", "EXPENSE", null, null);
        insertTx(statement, account, "2026-04-01", "300", "INCOME", null, "Refund");

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("PLN"))
                .andExpect(jsonPath("$.unconverted").value(0))
                .andExpect(jsonPath("$.totals.income").value(0.0))
                .andExpect(jsonPath("$.totals.expense").value(170.0))
                .andExpect(jsonPath("$.totals.net").value(-170.0))
                .andExpect(jsonPath("$.totals.count").value(3))
                .andExpect(jsonPath("$.totals.uncategorizedCount").value(1))
                .andExpect(jsonPath("$.totals.avgExpensePerDay").value(5.48))
                .andExpect(jsonPath("$.byMonth.length()").value(1))
                .andExpect(jsonPath("$.byMonth[0].month").value("2026-03"))
                .andExpect(jsonPath("$.byMonth[0].expense").value(170.0))
                .andExpect(jsonPath("$.byCategory.length()").value(2))
                .andExpect(jsonPath("$.byCategory[0].categoryName").value("Groceries"))
                .andExpect(jsonPath("$.byCategory[0].expense").value(150.0))
                .andExpect(jsonPath("$.byCategory[1].categoryName").value("(uncategorized)"))
                .andExpect(jsonPath("$.byCategory[1].expense").value(20.0))
                .andExpect(jsonPath("$.topMerchants[0].merchant").value("Example Store"))
                .andExpect(jsonPath("$.topMerchants[0].expense").value(150.0))
                .andExpect(jsonPath("$.topMerchants[0].count").value(2))
                .andExpect(jsonPath("$.topMerchants[1].merchant").value("(no merchant)"));

        // The April transaction is outside the requested range.
        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.income").value(300.0))
                .andExpect(jsonPath("$.totals.expense").value(0.0));
    }

    @Test
    void excludesInternalTransfersFromTotals() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "h2");
        insertTx(statement, account, "2026-03-10", "-100", "EXPENSE", null, "Example Store");
        insertTx(statement, account, "2026-03-11", "-5000", "TRANSFER", null, null);

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.expense").value(100.0))
                .andExpect(jsonPath("$.totals.count").value(1));
    }

    @Test
    void filtersByAccountKindAndAccountId() throws Exception {
        long personal = insertAccount("PLN", "PERSONAL");
        long business = insertAccount("PLN", "BUSINESS");
        long stmtPersonal = insertStatement(personal, "hp");
        long stmtBusiness = insertStatement(business, "hb");
        insertTx(stmtPersonal, personal, "2026-03-10", "-100", "EXPENSE", null, "Home");
        insertTx(stmtBusiness, business, "2026-03-10", "-300", "EXPENSE", null, "Supplies");
        insertTx(stmtBusiness, business, "2026-03-11", "1000", "INCOME", null, "Client");

        // Business view: revenue minus expenses over the period, transfers excluded.
        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("kind", "BUSINESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.income").value(1000.0))
                .andExpect(jsonPath("$.totals.expense").value(300.0))
                .andExpect(jsonPath("$.totals.net").value(700.0));

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("kind", "PERSONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.expense").value(100.0));

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("accountId", String.valueOf(business)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.expense").value(300.0));
    }

    @Test
    void returnsEmptyTotalsWhenNoAccountMatchesKind() throws Exception {
        long personal = insertAccount("PLN", "PERSONAL");
        long stmt = insertStatement(personal, "hp2");
        insertTx(stmt, personal, "2026-03-10", "-100", "EXPENSE", null, "Home");

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("kind", "BUSINESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.income").value(0.0))
                .andExpect(jsonPath("$.totals.expense").value(0.0))
                .andExpect(jsonPath("$.totals.count").value(0));
    }

    @Test
    void limitsTopMerchants() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "h3");
        insertTx(statement, account, "2026-03-10", "-100", "EXPENSE", null, "Big");
        insertTx(statement, account, "2026-03-11", "-1", "EXPENSE", null, "Small");

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("topN", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topMerchants.length()").value(1))
                .andExpect(jsonPath("$.topMerchants[0].merchant").value("Big"));
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        long account = insertAccount("PLN", null);

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-31")
                        .param("to", "2026-03-01"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/statistics/summary").param("accountId", "9999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/statistics/summary").param("kind", "NOT_A_KIND"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/statistics/summary").param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    private long insertAccount(String currency, String kind) {
        return jdbcTemplate.queryForObject("""
                insert into account (name, currency, kind, account_number)
                values (?, ?, ?, ?) returning id
                """, Long.class, "Account", currency, kind, "IBAN");
    }

    private long insertCategory(String name) {
        return jdbcTemplate.queryForObject("""
                insert into category (name, color, sort_order) values (?, '#4CAF50', 10) returning id
                """, Long.class, name);
    }

    private long insertStatement(long accountId, String hash) {
        return jdbcTemplate.queryForObject("""
                insert into bank_statement (account_id, bank, file_hash) values (?, 'TEST', ?) returning id
                """, Long.class, accountId, hash);
    }

    private void insertTx(long statementId, long accountId, String date, String amount, String nature,
                          Long categoryId, String merchant) {
        jdbcTemplate.update("""
                insert into transaction (statement_id, account_id, transaction_date, amount,
                    currency, nature, category_id, merchant, base_amount)
                values (?, ?, ?, ?, 'PLN', ?, ?, ?, ?)
                """, statementId, accountId, Date.valueOf(LocalDate.parse(date)),
                new BigDecimal(amount), nature, categoryId, merchant, new BigDecimal(amount));
    }
}
