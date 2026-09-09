package com.financedashboard.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedashboard.domain.port.FxRateProvider.FxRate;
import com.financedashboard.infrastructure.fx.NbpFxRateProvider;
import com.financedashboard.support.PekaoTestPdf;
import com.financedashboard.web.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies that importing a USD statement writes the transaction's value in every supported currency
 * at its own date (native USD exact, PLN converted at the resolved rate). The provider is stubbed at
 * a fixed rate so the test is offline.
 */
@TestPropertySource(properties = "finance.currencies=PLN,USD")
class FxImportApiTest extends AbstractIntegrationTest {

    @MockBean
    private NbpFxRateProvider fxRateProvider;

    @Test
    void importsForeignCurrencyStatementAndStoresEverySupportedCurrency() throws Exception {
        when(fxRateProvider.findRate(anyString(), any()))
                .thenAnswer(invocation -> Optional.of(
                        new FxRate(new BigDecimal("4.0"), invocation.getArgument(1))));

        long accountId = jdbcTemplate.queryForObject("""
                insert into account (name, currency, account_number)
                values ('Business USD', 'USD', '99000011112222333344445555') returning id
                """, Long.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "usd.pdf", "application/pdf", PekaoTestPdf.usdStatement());

        mockMvc.perform(multipart("/api/v1/statements").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(2));

        BigDecimal debitPln = jdbcTemplate.queryForObject("""
                select ta.amount from transaction_amount ta
                join transaction t on t.id = ta.transaction_id
                where t.account_id = ? and ta.currency = 'PLN' and ta.amount < 0
                """, BigDecimal.class, accountId);
        BigDecimal creditPln = jdbcTemplate.queryForObject("""
                select ta.amount from transaction_amount ta
                join transaction t on t.id = ta.transaction_id
                where t.account_id = ? and ta.currency = 'PLN' and ta.amount > 0
                """, BigDecimal.class, accountId);
        BigDecimal debitUsd = jdbcTemplate.queryForObject("""
                select ta.amount from transaction_amount ta
                join transaction t on t.id = ta.transaction_id
                where t.account_id = ? and ta.currency = 'USD' and ta.amount < 0
                """, BigDecimal.class, accountId);
        Integer childRows = jdbcTemplate.queryForObject("""
                select count(*) from transaction_amount ta
                join transaction t on t.id = ta.transaction_id
                where t.account_id = ?
                """, Integer.class, accountId);

        assertThat(debitPln).isEqualByComparingTo("-200.0000");
        assertThat(creditPln).isEqualByComparingTo("320.0000");
        assertThat(debitUsd).isEqualByComparingTo("-50.0000");
        assertThat(childRows).isEqualTo(4);
    }

    @Test
    void importFailsWholeStatementWhenTheRateIsUnresolvable() throws Exception {
        when(fxRateProvider.findRate(anyString(), any())).thenReturn(Optional.empty());

        long accountId = jdbcTemplate.queryForObject("""
                insert into account (name, currency, account_number)
                values ('Business USD', 'USD', '99000011112222333344445555') returning id
                """, Long.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "usd.pdf", "application/pdf", PekaoTestPdf.usdStatement());

        mockMvc.perform(multipart("/api/v1/statements").file(file))
                .andExpect(status().isUnprocessableEntity());

        assertThat((Integer) jdbcTemplate.queryForObject(
                "select count(*) from bank_statement where account_id = ?",
                Integer.class, accountId)).isZero();
        assertThat((Integer) jdbcTemplate.queryForObject(
                "select count(*) from transaction where account_id = ?",
                Integer.class, accountId)).isZero();
    }
}
