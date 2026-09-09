package com.financedashboard.infrastructure.persistence;

import com.financedashboard.infrastructure.persistence.entity.CategoryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@code category} table. */
public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findAllByOrderBySortOrderAscNameAsc();

    Optional<CategoryEntity> findFirstBySystemTrue();
}
