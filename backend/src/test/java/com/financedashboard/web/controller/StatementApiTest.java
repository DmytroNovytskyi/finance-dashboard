package com.financedashboard.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.web.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatementApiTest extends AbstractIntegrationTest {

    private long createAccount(String currency) {
        return jdbcTemplate.queryForObject("""
                insert into account (name, currency) values (?, ?) returning id
                """, Long.class, "Account " + currency, currency);
    }

    private long insertStatement(long accountId, String bank, String fileName, String hash, boolean now) {
        return jdbcTemplate.queryForObject("""
                insert into bank_statement (account_id, bank, file_name, file_hash, imported_at)
                values (?, ?, ?, ?, %s) returning id
                """.formatted(now ? "now()" : "now() - interval '1 hour'"),
                Long.class, accountId, bank, fileName, hash);
    }

    private long insertTransaction(long statementId, long accountId, String nature, String hash,
            UUID transferGroup) {
        return jdbcTemplate.queryForObject("""
                insert into transaction (statement_id, account_id, transaction_date, amount,
                        currency, nature, description, dedup_hash, transfer_group_id)
                values (?, ?, date '2026-08-05', -12.50, 'PLN', ?, 'op', ?, ?::uuid)
                returning id
                """, Long.class, statementId, accountId, nature, hash,
                transferGroup == null ? null : transferGroup.toString());
    }

    @Test
    void listsStatementsNewestFirstWithPerStatementCounts() throws Exception {
        long accountId = createAccount("PLN");
        long older = insertStatement(accountId, "PEKAO", "sierpień.pdf", "hash-old", false);
        long newer = insertStatement(accountId, "PEKAO", "wrzesień.pdf", "hash-new", true);
        insertTransaction(older, accountId, "EXPENSE", "d1", null);
        insertTransaction(older, accountId, "EXPENSE", "d2", null);
        insertTransaction(older, accountId, "EXPENSE", "d3", null);
        insertTransaction(newer, accountId, "EXPENSE", "d4", null);

        mockMvc.perform(get("/api/v1/statements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer))
                .andExpect(jsonPath("$[0].bank").value("PEKAO"))
                .andExpect(jsonPath("$[0].transactionCount").value(1))
                .andExpect(jsonPath("$[1].id").value(older))
                .andExpect(jsonPath("$[1].transactionCount").value(3));
    }

    @Test
    void deleteRemovesTheStatementAndOnlyItsTransactions() throws Exception {
        long accountId = createAccount("PLN");
        long doomed = insertStatement(accountId, "PEKAO", "sierpień.pdf", "hash-doom", true);
        long sibling = insertStatement(accountId, "PEKAO", "wrzesień.pdf", "hash-keep", false);
        insertTransaction(doomed, accountId, "EXPENSE", "d1", null);
        insertTransaction(doomed, accountId, "EXPENSE", "d2", null);
        insertTransaction(sibling, accountId, "EXPENSE", "d3", null);

        mockMvc.perform(delete("/api/v1/statements/{id}", doomed))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/statements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sibling));
        assertThat((Long) jdbcTemplate.queryForObject(
                "select count(*) from transaction where statement_id = ?", Long.class, sibling))
                .isEqualTo(1L);
        assertThat((Long) jdbcTemplate.queryForObject(
                "select count(*) from transaction where statement_id = ?", Long.class, doomed))
                .isZero();
    }

    @Test
    void deleteUnpairsSurvivingTransferLeg() throws Exception {
        long accountIdA = createAccount("PLN");
        long accountIdB = createAccount("USD");
        long statementA = insertStatement(accountIdA, "PEKAO", "pln.pdf", "hash-a", true);
        long statementB = insertStatement(accountIdB, "PEKAO", "usd.pdf", "hash-b", false);
        UUID group = UUID.randomUUID();
        long legA = insertTransaction(statementA, accountIdA, "TRANSFER", "da", group);
        long legB = insertTransaction(statementB, accountIdB, "TRANSFER", "db", group);

        mockMvc.perform(delete("/api/v1/statements/{id}", statementA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions/{id}", legB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nature").value("EXPENSE"))
                .andExpect(jsonPath("$.transferGroupId").doesNotExist());
        assertThat((Long) jdbcTemplate.queryForObject(
                "select count(*) from transaction where id = ?", Long.class, legA))
                .isZero();
    }

    @Test
    void deleteUnknownStatementReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/statements/424242"))
                .andExpect(status().isNotFound());
    }

    private long insertStatementWithPeriod(long accountId, String fileName, String hash,
            String periodStart, String periodEnd) {
        return jdbcTemplate.queryForObject("""
                insert into bank_statement (account_id, bank, file_name, file_hash, period_start,
                        period_end)
                values (?, 'PEKAO', ?, ?, ?::date, ?::date) returning id
                """, Long.class, accountId, fileName, hash, periodStart, periodEnd);
    }

    @Test
    void ordersStatementsByTheRequestedColumnAndDirection() throws Exception {
        long accountId = createAccount("PLN");
        long march = insertStatementWithPeriod(accountId, "marzec.pdf", "hash-mar",
                "2026-03-01", "2026-03-31");
        long january = insertStatementWithPeriod(accountId, "styczeń.pdf", "hash-jan",
                "2026-01-01", "2026-01-31");
        long february = insertStatementWithPeriod(accountId, "luty.pdf", "hash-feb",
                "2026-02-01", "2026-02-28");

        mockMvc.perform(get("/api/v1/statements").param("sort", "period").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(january))
                .andExpect(jsonPath("$[1].id").value(february))
                .andExpect(jsonPath("$[2].id").value(march));

        mockMvc.perform(get("/api/v1/statements").param("sort", "file").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(february))
                .andExpect(jsonPath("$[1].id").value(march))
                .andExpect(jsonPath("$[2].id").value(january));

        mockMvc.perform(get("/api/v1/statements").param("sort", "period").param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(march));
    }

    @Test
    void ordersStatementsByAccountName() throws Exception {
        long personal = createAccount("PLN");
        long business = createAccount("USD");
        long personalStatement = insertStatement(personal, "PEKAO", "pln.pdf", "hash-p", true);
        long businessStatement = insertStatement(business, "PEKAO", "usd.pdf", "hash-b", true);

        mockMvc.perform(get("/api/v1/statements").param("sort", "account").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(personalStatement))
                .andExpect(jsonPath("$[1].id").value(businessStatement));

        mockMvc.perform(get("/api/v1/statements").param("sort", "account").param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(businessStatement))
                .andExpect(jsonPath("$[1].id").value(personalStatement));
    }

    @Test
    void filtersStatementsToOneAccount() throws Exception {
        long kept = createAccount("PLN");
        long other = createAccount("USD");
        long keptStatement = insertStatement(kept, "PEKAO", "pln.pdf", "hash-kept", true);
        insertStatement(other, "PEKAO", "usd.pdf", "hash-other", true);

        mockMvc.perform(get("/api/v1/statements").param("accountId", String.valueOf(kept)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(keptStatement));
    }

    @Test
    void fallsBackToNewestImportForAnUnknownSortColumn() throws Exception {
        long accountId = createAccount("PLN");
        long older = insertStatement(accountId, "PEKAO", "sierpień.pdf", "hash-old", false);
        long newer = insertStatement(accountId, "PEKAO", "wrzesień.pdf", "hash-new", true);

        mockMvc.perform(get("/api/v1/statements").param("sort", "nonsense").param("order", "sideways"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newer))
                .andExpect(jsonPath("$[1].id").value(older));
    }
}
