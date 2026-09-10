package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.domain.port.AccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accounts;

    @InjectMocks
    private AccountService service;

    @Test
    void createNormalizesNameAndCurrency() {
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Account created = service.create("  Personal PLN ", "pln", AccountKind.PERSONAL, "123", null);

        assertThat(created.getName()).isEqualTo("Personal PLN");
        assertThat(created.getCurrency()).isEqualTo("PLN");
        assertThat(created.getKind()).isEqualTo(AccountKind.PERSONAL);
        assertThat(created.getAccountNumber()).isEqualTo("123");
        assertThat(created.getSortOrder()).isZero();
    }

    @Test
    void createCanonicalizesAccountNumberToDigitsOnly() {
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Account created = service.create("Pekao", "PLN", null,
                "PL 00 0000 0000 0000 0000 0000 0001", null);

        assertThat(created.getAccountNumber()).isEqualTo("00000000000000000000000001");
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> service.create("   ", "PLN", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsNonIsoCurrency() {
        assertThatThrownBy(() -> service.create("Account", "PL", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getThrowsWhenAccountMissing() {
        when(accounts.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(NotFoundException.class);
    }

    private static Account storedAccount(String accountNumber) {
        return Account.builder()
                .id(1L)
                .name("Old")
                .currency("PLN")
                .accountNumber(accountNumber)
                .sortOrder(0)
                .build();
    }

    private void givenStored(Account existing) {
        when(accounts.findById(1L)).thenReturn(Optional.of(existing));
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateRenamesTheAccountAndKeepsTheServerOwnedFields() {
        givenStored(storedAccount("123"));

        Account updated = service.update(1L, "New", AccountKind.BUSINESS);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getCurrency()).isEqualTo("PLN");
        assertThat(updated.getKind()).isEqualTo(AccountKind.BUSINESS);
        assertThat(updated.getAccountNumber()).isEqualTo("123");
        verify(accounts).save(any());
    }

    @Test
    void updateClearsTheKindWhenNoneIsGiven() {
        givenStored(storedAccount("123").toBuilder().kind(AccountKind.PERSONAL).build());

        Account updated = service.update(1L, "New", null);

        assertThat(updated.getKind()).isNull();
    }

    @Test
    void updateTrimsTheName() {
        givenStored(storedAccount("123"));

        Account updated = service.update(1L, "  Renamed  ", null);

        assertThat(updated.getName()).isEqualTo("Renamed");
    }

    @Test
    void updateRejectsBlankName() {
        when(accounts.findById(1L)).thenReturn(Optional.of(storedAccount("123")));

        assertThatThrownBy(() -> service.update(1L, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
