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

/**
 * Verifies that importing a USD statement converts amounts to the PLN base using the resolved FX
 * rate and stores the conversion. The provider is stubbed at a fixed rate so the test is offline.
 */
class FxImportApiTest extends AbstractIntegrationTest {

    @MockBean
    private NbpFxRateProvider fxRateProvider;

    @Test
    void importsForeignCurrencyStatementIntoBaseCurrency() throws Exception {
        when(fxRateProvider.findRate(anyString(), any()))
                .thenAnswer(invocation -> Optional.of(
                        new FxRate(new BigDecimal("4.0"), invocation.getArgument(1))));

        long accountId = jdbcTemplate.queryForObject("""
                insert into account (name, currency) values ('Business USD', 'USD') returning id
                """, Long.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "usd.pdf", "application/pdf", PekaoTestPdf.usdStatement());

        mockMvc.perform(multipart("/api/v1/statements")
                        .file(file)
                        .param("accountId", String.valueOf(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(2));

        BigDecimal debitBase = jdbcTemplate.queryForObject(
                "select base_amount from transaction where account_id = ? and amount < 0",
                BigDecimal.class, accountId);
        BigDecimal creditBase = jdbcTemplate.queryForObject(
                "select base_amount from transaction where account_id = ? and amount > 0",
                BigDecimal.class, accountId);
        BigDecimal rate = jdbcTemplate.queryForObject(
                "select fx_rate from transaction where account_id = ? and amount < 0",
                BigDecimal.class, accountId);

        assertThat(debitBase).isEqualByComparingTo("-200.0000");
        assertThat(creditBase).isEqualByComparingTo("320.0000");
        assertThat(rate).isEqualByComparingTo("4.0");
    }
}
