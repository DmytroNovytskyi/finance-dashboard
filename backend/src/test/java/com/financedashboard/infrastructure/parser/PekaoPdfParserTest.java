package com.financedashboard.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.financedashboard.domain.statement.ParsedStatement;
import com.financedashboard.domain.statement.ParsedTransaction;
import com.financedashboard.support.PekaoTestPdf;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PekaoPdfParserTest {

    private final PekaoPdfParser parser = new PekaoPdfParser();

    @Test
    void supportsPdfMediaType() {
        assertThat(parser.supports("application/pdf")).isTrue();
        assertThat(parser.supports("text/plain")).isFalse();
    }

    @Test
    void recognizesTheDocument() {
        assertThat(parser.canParse(PekaoTestPdf.threeTransactions())).isTrue();
        assertThat(parser.canParse("not a pdf".getBytes())).isFalse();
    }

    @Test
    void parsesPeriodCurrencyBankAndAccountNumber() {
        ParsedStatement statement = parser.parse(PekaoTestPdf.threeTransactions());

        assertThat(statement.bank()).isEqualTo("PEKAO");
        assertThat(statement.currency()).isEqualTo("PLN");
        assertThat(statement.accountNumber()).isEqualTo("00000000000000000000000002");
        assertThat(statement.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(statement.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 25));
    }


    @Test
    void parsesTransactionsReconcilingToStatementTotals() {
        ParsedStatement statement = parser.parse(PekaoTestPdf.threeTransactions());

        assertThat(statement.transactions()).hasSize(3);

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (ParsedTransaction tx : statement.transactions()) {
            assertThat(tx.date()).isBetween(statement.periodStart(), statement.periodEnd());
            assertThat(tx.description()).isNotBlank();
            if (tx.amount().signum() < 0) {
                debits = debits.add(tx.amount());
            } else {
                credits = credits.add(tx.amount());
            }
        }
        assertThat(debits).isEqualByComparingTo("-83.75");
        assertThat(credits).isEqualByComparingTo("1200.50");
    }

    @Test
    void extractsMerchants() {
        ParsedStatement statement = parser.parse(PekaoTestPdf.threeTransactions());

        assertThat(statement.transactions()).anySatisfy(tx ->
                assertThat(tx.merchant()).contains("FAKE MERCHANT"));
        assertThat(statement.transactions()).anySatisfy(tx ->
                assertThat(tx.merchant()).contains("BLIK MERCHANT"));
    }
}
