package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Use cases for managing spending categories. */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categories;

    /** Returns all categories in display order. */
    public List<Category> list() {
        return categories.findAll();
    }

    /** Returns the category with the given id. */
    public Category get(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Category " + id + " not found"));
    }

    /** Creates a new category. */
    public Category create(String name, String color, Integer sortOrder) {
        Category category = Category.builder()
                .name(requireName(name))
                .color(color)
                .sortOrder(sortOrder == null ? 0 : sortOrder)
                .build();
        return categories.save(category);
    }

    /** Applies the non-null fields to the category with the given id. */
    public Category update(Long id, String name, String color) {
        Category current = get(id);
        Category updated = current.toBuilder()
                .name(name != null ? requireName(name) : current.getName())
                .color(color != null ? color : current.getColor())
                .build();
        return categories.save(updated);
    }

    /** Deletes the category; its transactions become uncategorized. System categories are kept. */
    public void delete(Long id) {
        Category current = get(id);
        if (current.isSystem()) {
            throw new IllegalArgumentException("Category '" + current.getName() + "' is required and cannot be deleted");
        }
        categories.deleteById(id);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.trim();
    }
}
