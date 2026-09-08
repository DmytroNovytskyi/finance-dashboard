package com.financedashboard.domain.port;

import com.financedashboard.domain.category.Category;
import java.util.List;
import java.util.Optional;

/** Outbound port for storing and reading {@link Category} aggregates. */
public interface CategoryRepository {

    /** Saves the category and returns the stored state (with its generated id). */
    Category save(Category category);

    /** Returns the category with the given id, if present. */
    Optional<Category> findById(Long id);

    /** Returns all categories ordered by sort order then name. */
    List<Category> findAll();

    /** Returns whether a category with the given id exists. */
    boolean existsById(Long id);

    /** Deletes the category with the given id; affected transactions become uncategorized. */
    void deleteById(Long id);
}
