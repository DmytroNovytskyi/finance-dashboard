package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categories;

    @InjectMocks
    private CategoryService service;

    @Test
    void createDefaultsSortOrderToZero() {
        when(categories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Category created = service.create("Groceries", "#4CAF50", null);

        assertThat(created.getName()).isEqualTo("Groceries");
        assertThat(created.getColor()).isEqualTo("#4CAF50");
        assertThat(created.getSortOrder()).isZero();
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> service.create("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateChangesNameAndKeepsColor() {
        Category existing = Category.builder().id(1L).name("Old").color("#000000").sortOrder(1).build();
        when(categories.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(categories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Category updated = service.update(1L, "New", null);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getColor()).isEqualTo("#000000");
    }

    @Test
    void deleteRejectsSystemCategory() {
        Category system = Category.builder().id(2L).name("Transfer").system(true).build();
        when(categories.findById(2L)).thenReturn(java.util.Optional.of(system));

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteThrowsWhenCategoryMissing() {
        when(categories.findById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(NotFoundException.class);
        verify(categories, never()).deleteById(any());
    }
}
