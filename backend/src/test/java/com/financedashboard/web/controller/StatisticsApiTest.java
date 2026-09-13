package com.financedashboard.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Statistics read the stored per-transaction value in the requested currency. Rows are written
 * directly (native PLN amount plus the exact child value); no FX provider is involved, so the
 * class also enables USD to exercise the display-currency summing path.
 */
@TestPropertySource(properties = "finance.currencies=PLN,USD")
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
    void excludesABalancedRefundGroupFromTotals() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "h3r");
        long purchase = insertTx(statement, account, "2026-03-12", "-250", "REFUND", null, "Example Shop");
        long credit = insertTx(statement, account, "2026-03-12", "250", "REFUND", null, null);
        linkAsRefund(UUID.randomUUID(), purchase, credit);
        insertTx(statement, account, "2026-03-10", "-100", "EXPENSE", null, "Example Store");

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.income").value(0.0))
                .andExpect(jsonPath("$.totals.expense").value(100.0))
                .andExpect(jsonPath("$.totals.net").value(-100.0))
                .andExpect(jsonPath("$.totals.count").value(1));
    }

    @Test
    void foldsAnUnbalancedRefundGroupIntoOneRowOnItsLastLeg() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "h3f");
        long refundCategory = insertCategory("Refund");
        long januaryCredit = insertTx(statement, account, "2026-01-18", "500", "REFUND", refundCategory, "Example Payee");
        long februaryCredit = insertTx(statement, account, "2026-02-18", "2500", "REFUND", refundCategory, "Example Payee");
        long firstPayment = insertTx(statement, account, "2026-02-19", "-1000", "REFUND", refundCategory, "Example Payee");
        long secondPayment = insertTx(statement, account, "2026-02-19", "-1000", "REFUND", refundCategory, "Example Payee");
        linkAsRefund(UUID.randomUUID(), januaryCredit, februaryCredit, firstPayment, secondPayment);

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-02-01")
                        .param("to", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.income").value(500.0))
                .andExpect(jsonPath("$.totals.expense").value(0.0))
                .andExpect(jsonPath("$.totals.net").value(500.0))
                .andExpect(jsonPath("$.totals.count").value(3))
                .andExpect(jsonPath("$.byMonth[0].month").value("2026-02"))
                .andExpect(jsonPath("$.byCategory[0].categoryName").value("Refund"))
                .andExpect(jsonPath("$.byCategory[0].income").value(500.0));

        // Only the January leg falls in this range, so it folds to its own net and date.
        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.income").value(500.0))
                .andExpect(jsonPath("$.totals.count").value(1));
    }

    private void linkAsRefund(UUID group, long... transactionIds) {
        for (long id : transactionIds) {
            jdbcTemplate.update("update transaction set refund_group_id = ? where id = ?", group, id);
        }
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

        mockMvc.perform(get("/api/v1/statistics/summary").param("granularity", "not-a-granularity"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trendBucketsFollowGranularityParam() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "ht");
        insertTx(statement, account, "2026-03-10", "-100", "EXPENSE", null, "Shop");
        insertTx(statement, account, "2026-03-11", "-50", "EXPENSE", null, "Shop");
        insertTx(statement, account, "2026-04-01", "-30", "EXPENSE", null, "Shop");

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-04-30")
                        .param("granularity", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend.length()").value(3))
                .andExpect(jsonPath("$.trend[0].start").value("2026-03-10"))
                .andExpect(jsonPath("$.trend[0].expense").value(100.0))
                .andExpect(jsonPath("$.trend[2].expense").value(30.0));

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("granularity", "month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend.length()").value(2));
    }

    @Test
    void sumsStoredUsdChildValuesWhenDisplayCurrencyIsRequested() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "hfx");
        long first = insertTx(statement, account, "2026-03-10", "-100", "EXPENSE", null, "Shop");
        long second = insertTx(statement, account, "2026-03-20", "-70", "EXPENSE", null, "Shop");
        insertAmount(first, "USD", "-25");
        insertAmount(second, "USD", "-17.5");

        mockMvc.perform(get("/api/v1/statistics/summary")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("displayCurrency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.totals.expense").value(42.5))
                .andExpect(jsonPath("$.totals.net").value(-42.5));
    }

    @Test
    void categoryTransactionsReturnsOnlyThatCategorysRows() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "hct");
        long groceries = insertCategory("Groceries");
        long other = insertCategory("Other");
        insertTx(statement, account, "2026-03-10", "-100", "EXPENSE", groceries, "Example Store");
        insertTx(statement, account, "2026-03-11", "-50", "EXPENSE", groceries, "Example Store");
        insertTx(statement, account, "2026-03-12", "-300", "EXPENSE", other, "Shop");

        mockMvc.perform(get("/api/v1/statistics/categories/" + groceries + "/transactions")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("PLN"))
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$.points[0].amount").value(-100.0))
                .andExpect(jsonPath("$.points[1].date").value("2026-03-11"))
                .andExpect(jsonPath("$.points[1].amount").value(-50.0));

        mockMvc.perform(get("/api/v1/statistics/categories/9999/transactions"))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryTrendBucketsByGranularity() throws Exception {
        long account = insertAccount("PLN", null);
        long statement = insertStatement(account, "hct2");
        long groceries = insertCategory("Groceries");
        insertTx(statement, account, "2026-03-10", "-100", "EXPENSE", groceries, "Example Store");
        insertTx(statement, account, "2026-03-11", "-50", "EXPENSE", groceries, "Example Store");

        mockMvc.perform(get("/api/v1/statistics/categories/" + groceries + "/trend")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("granularity", "month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("PLN"))
                .andExpect(jsonPath("$.trend.length()").value(1))
                .andExpect(jsonPath("$.trend[0].expense").value(150.0));
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

    private long insertTx(long statementId, long accountId, String date, String amount, String nature,
                          Long categoryId, String merchant) {
        long id = jdbcTemplate.queryForObject("""
                insert into transaction (statement_id, account_id, transaction_date, amount,
                    currency, nature, category_id, merchant)
                values (?, ?, ?, ?, 'PLN', ?, ?, ?) returning id
                """, Long.class, statementId, accountId, Date.valueOf(LocalDate.parse(date)),
                new BigDecimal(amount), nature, categoryId, merchant);
        insertAmount(id, "PLN", amount);
        return id;
    }

    private void insertAmount(long transactionId, String currency, String amount) {
        jdbcTemplate.update("""
                insert into transaction_amount (transaction_id, currency, amount) values (?, ?, ?)
                """, transactionId, currency, new BigDecimal(amount));
    }
}
