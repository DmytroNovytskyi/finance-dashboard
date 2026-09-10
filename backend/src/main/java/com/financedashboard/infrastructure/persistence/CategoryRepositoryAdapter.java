package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.infrastructure.persistence.mapper.CategoryMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Spring Data implementation of {@link CategoryRepository}. */
@Component
@RequiredArgsConstructor
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;
    private final CategoryMapper mapper;

    @Override
    public Category save(Category category) {
        return mapper.toDomain(jpa.save(mapper.toEntity(category)));
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return mapper.toDomain(jpa.findAllByOrderBySortOrderAscNameAsc());
    }

    @Override
    public Optional<Category> findSystemCategory(String systemKey) {
        return jpa.findFirstBySystemKey(systemKey).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
