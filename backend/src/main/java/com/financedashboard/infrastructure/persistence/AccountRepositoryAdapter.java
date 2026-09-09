package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.infrastructure.persistence.mapper.AccountMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Spring Data implementation of {@link AccountRepository}. */
@Component
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;
    private final AccountMapper mapper;

    @Override
    public Account save(Account account) {
        return mapper.toDomain(jpa.save(mapper.toEntity(account)));
    }

    @Override
    public Optional<Account> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Account> findAll() {
        return mapper.toDomain(jpa.findAllByOrderBySortOrderAscNameAsc());
    }

    @Override
    public List<Account> findByKind(AccountKind kind) {
        return mapper.toDomain(jpa.findByKindOrderBySortOrderAscNameAsc(kind));
    }

    @Override
    public boolean existsById(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return jpa.findFirstByAccountNumberOrderByIdAsc(accountNumber).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findFirstByCurrency(String currency) {
        return jpa.findFirstByCurrencyOrderBySortOrderAscIdAsc(currency).map(mapper::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
