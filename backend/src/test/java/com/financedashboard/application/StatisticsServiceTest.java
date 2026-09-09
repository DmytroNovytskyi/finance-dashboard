package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.application.StatisticsService.CategoryTotal;
import com.financedashboard.application.StatisticsService.MerchantTotal;
import com.financedashboard.application.StatisticsService.MonthlyTotal;
import com.financedashboard.application.StatisticsService.Summary;
import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    private static final BigDecimal PLN_100 = new BigDecimal("-100");
    private static final BigDecimal PLN_50 = new BigDecimal("-50");
    private static final BigDecimal PLN_20 = new BigDecimal("-20");
    private static final BigDecimal PLN_300 = new BigDecimal("300");

    @Mock
    private TransactionRepository transactions;
    @Mock
    private AccountRepository accounts;
    @Mock
    private CategoryRepository categories;

    @InjectMocks
    private StatisticsService service;

    private Category groceries;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseCurrency", "PLN");
        groceries = Category.builder().id(1L).name("Groceries").color("#4CAF50").sortOrder(10).build();
    }

    @Test
    void summarizesIncomeExpenseAndGroupsByMonthCategoryAndMerchant() {
        when(categories.findAll()).thenReturn(List.of(groceries));
        when(transactions.findNonTransfers(eq(LocalDate.of(2026, 3, 1)),
                eq(LocalDate.of(2026, 3, 31)), any())).thenReturn(List.of(
                tx(1L, 1L, LocalDate.of(2026, 3, 10), PLN_100, PLN_100, 1L, "Example Store"),
                tx(2L, 1L, LocalDate.of(2026, 3, 12), PLN_50, PLN_50, 1L, "Example Store"),
                tx(3L, 1L, LocalDate.of(2026, 3, 20), PLN_20, PLN_20, null, null)));

        Summary s = service.summary(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null, null, null);

        assertThat(s.totals().income()).isEqualByComparingTo("0");
        assertThat(s.totals().expense()).isEqualByComparingTo("170");
        assertThat(s.totals().net()).isEqualByComparingTo("-170");
        assertThat(s.totals().count()).isEqualTo(3);
        assertThat(s.totals().uncategorizedCount()).isEqualTo(1);
        assertThat(s.totals().avgExpensePerDay()).isEqualByComparingTo("5.48");

        MonthlyTotal month = s.byMonth().get(0);
        assertThat(month.month()).isEqualTo("2026-03");
        assertThat(month.expense()).isEqualByComparingTo("170");

        assertThat(s.byCategory()).hasSize(2);
        CategoryTotal groceriesTotal = s.byCategory().get(0);
        assertThat(groceriesTotal.categoryId()).isEqualTo(1L);
        assertThat(groceriesTotal.categoryName()).isEqualTo("Groceries");
        assertThat(groceriesTotal.color()).isEqualTo("#4CAF50");
        assertThat(groceriesTotal.expense()).isEqualByComparingTo("150");
        CategoryTotal uncategorized = s.byCategory().get(1);
        assertThat(uncategorized.categoryId()).isNull();
        assertThat(uncategorized.categoryName()).isEqualTo("(uncategorized)");
        assertThat(uncategorized.expense()).isEqualByComparingTo("20");

        List<MerchantTotal> merchants = s.topMerchants();
        assertThat(merchants).hasSize(2);
        assertThat(merchants.get(0).merchant()).isEqualTo("Example Store");
        assertThat(merchants.get(0).count()).isEqualTo(2);
        assertThat(merchants.get(0).expense()).isEqualByComparingTo("150");
        assertThat(merchants.get(1).merchant()).isEqualTo("(no merchant)");
    }

    @Test
    void splitsIncomeAndExpenseAcrossMonths() {
        when(categories.findAll()).thenReturn(List.of(groceries));
        when(transactions.findNonTransfers(any(), any(), any())).thenReturn(List.of(
                tx(1L, 1L, LocalDate.of(2026, 3, 31), PLN_100, PLN_100, 1L, "Example Store"),
                tx(2L, 1L, LocalDate.of(2026, 4, 1), PLN_300, PLN_300, 1L, "Refund")));

        Summary s = service.summary(null, null, null, null, null);

        assertThat(s.totals().income()).isEqualByComparingTo("300");
        assertThat(s.totals().expense()).isEqualByComparingTo("100");
        assertThat(s.totals().net()).isEqualByComparingTo("200");
        assertThat(s.byMonth()).extracting(MonthlyTotal::month)
                .containsExactly("2026-03", "2026-04");
        assertThat(s.byMonth().get(0).net()).isEqualByComparingTo("-100");
        assertThat(s.byMonth().get(1).net()).isEqualByComparingTo("300");
    }

    @Test
    void reportsRowsWithoutBaseAmountAsUnconverted() {
        when(categories.findAll()).thenReturn(List.of());
        when(transactions.findNonTransfers(any(), any(), any())).thenReturn(List.of(
                tx(1L, 1L, LocalDate.of(2026, 3, 10), new BigDecimal("-80"), null, null, "Shop")));

        Summary s = service.summary(null, null, null, null, null);

        assertThat(s.unconverted()).isEqualTo(1);
        assertThat(s.totals().count()).isZero();
        assertThat(s.totals().expense()).isEqualByComparingTo("0");
        assertThat(s.byMonth()).isEmpty();
    }

    @Test
    void limitsMerchantsToTopN() {
        when(categories.findAll()).thenReturn(List.of(groceries));
        when(transactions.findNonTransfers(any(), any(), any())).thenReturn(List.of(
                tx(1L, 1L, LocalDate.of(2026, 3, 10), new BigDecimal("-1"), new BigDecimal("-1"), 1L, "M1"),
                tx(2L, 1L, LocalDate.of(2026, 3, 10), new BigDecimal("-2"), new BigDecimal("-2"), 1L, "M2")));

        Summary s = service.summary(null, null, null, null, 1);

        assertThat(s.topMerchants()).extracting(MerchantTotal::merchant).containsExactly("M2");
    }

    @Test
    void restrictsToAccountKindAndBusinessViewIsNetRevenueMinusExpenses() {
        Account business = Account.builder().id(7L).name("Biz").currency("PLN")
                .kind(AccountKind.BUSINESS).build();
        when(categories.findAll()).thenReturn(List.of(groceries));
        when(accounts.findByKind(AccountKind.BUSINESS)).thenReturn(List.of(business));
        when(transactions.findNonTransfers(any(), any(), eq(List.of(7L)))).thenReturn(List.of(
                tx(1L, 7L, LocalDate.of(2026, 3, 10), PLN_300, PLN_300, null, "Client"),
                tx(2L, 7L, LocalDate.of(2026, 3, 12), PLN_100, PLN_100, null, "Supplies")));

        Summary s = service.summary(null, null, null, AccountKind.BUSINESS, null);

        assertThat(s.totals().income()).isEqualByComparingTo("300");
        assertThat(s.totals().expense()).isEqualByComparingTo("100");
        assertThat(s.totals().net()).isEqualByComparingTo("200");
    }

    @Test
    void restrictsToOneAccount() {
        when(categories.findAll()).thenReturn(List.of(groceries));
        when(accounts.existsById(9L)).thenReturn(true);
        when(transactions.findNonTransfers(any(), any(), eq(List.of(9L)))).thenReturn(List.of(
                tx(1L, 9L, LocalDate.of(2026, 3, 10), PLN_100, PLN_100, null, "Shop")));

        Summary s = service.summary(null, null, 9L, null, null);

        assertThat(s.totals().expense()).isEqualByComparingTo("100");
        verify(accounts).existsById(9L);
    }

    @Test
    void rejectsUnknownAccount() {
        when(accounts.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.summary(null, null, 99L, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.summary(
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void groupsTrendByGranularityBucket() {
        when(categories.findAll()).thenReturn(List.of(groceries));
        when(transactions.findNonTransfers(any(), any(), any())).thenReturn(List.of(
                tx(1L, 1L, LocalDate.of(2026, 3, 10), PLN_100, PLN_100, 1L, "Shop"),
                tx(2L, 1L, LocalDate.of(2026, 3, 11), PLN_20, PLN_20, 1L, "Shop")));

        Summary byDay = service.summary(null, null, null, null, null, TrendGranularity.DAY);
        assertThat(byDay.trend()).extracting(StatisticsService.TrendPoint::start)
                .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11));
        assertThat(byDay.trend().get(1).expense()).isEqualByComparingTo("20");

        Summary byMonth = service.summary(null, null, null, null, null);
        assertThat(byMonth.trend()).hasSize(1);
        assertThat(byMonth.trend().get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(byMonth.trend().get(0).end()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(byMonth.trend().get(0).expense()).isEqualByComparingTo("120");
    }

    @Test
    void bucketBoundariesAndParsingCoverEveryGranularity() {
        LocalDate midMonth = LocalDate.of(2026, 3, 18);

        assertThat(TrendGranularity.DAY.bucketOf(midMonth).start()).isEqualTo(midMonth);
        assertThat(TrendGranularity.WEEK.bucketOf(LocalDate.of(2026, 3, 4)))
                .isEqualTo(new TrendGranularity.Bucket(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 8)));
        assertThat(TrendGranularity.MONTH.bucketOf(midMonth).end()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(TrendGranularity.QUARTER.bucketOf(LocalDate.of(2026, 2, 15)))
                .isEqualTo(new TrendGranularity.Bucket(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
        assertThat(TrendGranularity.QUARTER.bucketOf(LocalDate.of(2026, 11, 20)).start())
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(TrendGranularity.YEAR.bucketOf(midMonth).end()).isEqualTo(LocalDate.of(2026, 12, 31));

        assertThat(TrendGranularity.from("month")).isEqualTo(TrendGranularity.MONTH);
        assertThat(TrendGranularity.from("  DAY ")).isEqualTo(TrendGranularity.DAY);
        assertThat(TrendGranularity.from(null)).isEqualTo(TrendGranularity.MONTH);
        assertThatThrownBy(() -> TrendGranularity.from("fortnight"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Transaction tx(long id, long accountId, LocalDate date, BigDecimal amount,
                                  BigDecimal baseAmount, Long categoryId, String merchant) {
        return Transaction.builder()
                .id(id)
                .statementId(1L)
                .accountId(accountId)
                .transactionDate(date)
                .amount(amount)
                .currency(baseAmount == null ? "USD" : "PLN")
                .nature(TransactionNature.forSignedAmount(amount))
                .categoryId(categoryId)
                .merchant(merchant)
                .baseAmount(baseAmount)
                .build();
    }
}
