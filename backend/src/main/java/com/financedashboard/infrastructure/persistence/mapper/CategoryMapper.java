package com.financedashboard.infrastructure.persistence.mapper;

import com.financedashboard.domain.category.Category;
import com.financedashboard.infrastructure.persistence.entity.CategoryEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Maps between the {@link Category} aggregate and its JPA entity. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    Category toDomain(CategoryEntity entity);

    CategoryEntity toEntity(Category category);

    List<Category> toDomain(List<CategoryEntity> entities);
}
