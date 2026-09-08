package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.TransactionFilter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactions;

    @InjectMocks
    private TransactionService service;

    private static final TransactionFilter NO_FILTER =
            new TransactionFilter(null, null, null, null, null, null, null);

    @Test
    void listDefaultsPageZeroAndSizeFifty() {
        when(transactions.search(eq(NO_FILTER), eq(0), eq(50)))
                .thenReturn(new PagedTransactions(List.of(), 0, 0, 50));

        service.list(NO_FILTER, null, null);

        verify(transactions).search(eq(NO_FILTER), eq(0), eq(50));
    }

    @Test
    void listClampsPagingToAllowedBounds() {
        when(transactions.search(eq(NO_FILTER), eq(0), eq(200)))
                .thenReturn(new PagedTransactions(List.of(), 0, 0, 200));
        when(transactions.search(eq(NO_FILTER), eq(0), eq(1)))
                .thenReturn(new PagedTransactions(List.of(), 0, 0, 1));

        service.list(NO_FILTER, -5, 5000);
        service.list(NO_FILTER, 0, 0);

        verify(transactions).search(eq(NO_FILTER), eq(0), eq(200));
        verify(transactions).search(eq(NO_FILTER), eq(0), eq(1));
    }

    @Test
    void getThrowsWhenTransactionMissing() {
        when(transactions.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(7L)).isInstanceOf(NotFoundException.class);
    }
}
