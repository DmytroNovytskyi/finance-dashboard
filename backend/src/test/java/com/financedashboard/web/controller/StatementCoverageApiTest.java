package com.financedashboard.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Covers the coverage endpoint's wiring and payload shape. Assertions avoid the missing-period
 * dates, which depend on the real current date; those rules are pinned down by the domain tests.
 */
class StatementCoverageApiTest extends AbstractIntegrationTest {

    private long createAccount(String name, String currency) {
        return jdbcTemplate.queryForObject("""
                insert into account (name, currency) values (?, ?) returning id
                """, Long.class, name, currency);
    }

    private long insertStatement(long accountId, LocalDate start, LocalDate end, String hash) {
        return jdbcTemplate.queryForObject("""
                insert into bank_statement (account_id, bank, period_start, period_end, file_name, file_hash)
                values (?, 'PEKAO', ?, ?, 'wyciag.pdf', ?) returning id
                """, Long.class, accountId, Date.valueOf(start), Date.valueOf(end), hash);
    }

    private long insertStatementWithoutPeriod(long accountId, String hash) {
        return jdbcTemplate.queryForObject("""
                insert into bank_statement (account_id, bank, file_name, file_hash)
                values (?, 'PEKAO', 'unknown.pdf', ?) returning id
                """, Long.class, accountId, hash);
    }

    @Test
    void reportsBoundsCountsAndTheGapLeftByASkippedStatement() throws Exception {
        long accountId = createAccount("Pekao Personal PLN", "PLN");
        insertStatement(accountId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "h1");
        insertStatement(accountId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "h2");
        insertStatement(accountId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "h3");

        mockMvc.perform(get("/api/v1/statements/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(accountId))
                .andExpect(jsonPath("$[0].earliestPeriodStart").value("2026-01-01"))
                .andExpect(jsonPath("$[0].latestPeriodEnd").value("2026-04-30"))
                .andExpect(jsonPath("$[0].statementCount").value(3))
                .andExpect(jsonPath("$[0].gaps.length()").value(1))
                .andExpect(jsonPath("$[0].gaps[0].from").value("2026-03-01"))
                .andExpect(jsonPath("$[0].gaps[0].to").value("2026-03-31"))
                .andExpect(jsonPath("$[0].missingPeriodEnds").isArray());
    }

    @Test
    void reportsNoGapsWhenPeriodsAreContiguous() throws Exception {
        long accountId = createAccount("Pekao Business PLN", "PLN");
        insertStatement(accountId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "h1");
        insertStatement(accountId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "h2");

        mockMvc.perform(get("/api/v1/statements/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gaps.length()").value(0));
    }

    @Test
    void ignoresStatementsThatCarryNoPeriod() throws Exception {
        long accountId = createAccount("Pekao Personal USD", "USD");
        insertStatementWithoutPeriod(accountId, "h0");
        insertStatement(accountId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "h1");

        mockMvc.perform(get("/api/v1/statements/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statementCount").value(2))
                .andExpect(jsonPath("$[0].earliestPeriodStart").value("2026-01-01"))
                .andExpect(jsonPath("$[0].gaps.length()").value(0));
    }

    @Test
    void includesAnAccountThatHasNeverHadAStatement() throws Exception {
        long accountId = createAccount("Unused", "EUR");

        mockMvc.perform(get("/api/v1/statements/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(accountId))
                .andExpect(jsonPath("$[0].statementCount").value(0))
                .andExpect(jsonPath("$[0].missingPeriodEnds").isEmpty());
    }
}
