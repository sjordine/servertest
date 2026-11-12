package com.servertest.products.category;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.servertest.products.category.CategoryDtos.CategoryRequest;
import com.servertest.products.category.CategoryDtos.CategoryResponse;
import com.servertest.products.common.ApiException;
import com.servertest.products.common.Keys;

@Service
@Transactional
public class CategoryService {

    private final ProductCategoryRepository categories;

    public CategoryService(ProductCategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean includeArchived) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : categories.countActiveProductsByCategory()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        var rows = includeArchived ? categories.findAllByOrderByNameAsc()
                : categories.findAllByArchivedAtIsNullOrderByNameAsc();
        return rows.stream().map(c -> CategoryResponse.of(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return CategoryResponse.of(load(id), 0);
    }

    public CategoryResponse create(CategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Category '" + name + "' already exists");
        }
        return CategoryResponse.of(categories.save(new ProductCategory(name, Keys.trimToNull(req.description()))), 0);
    }

    public CategoryResponse update(UUID id, CategoryRequest req) {
        ProductCategory category = load(id);
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("Category '" + name + "' already exists");
        }
        category.setName(name);
        category.setDescription(Keys.trimToNull(req.description()));
        return CategoryResponse.of(category, 0);
    }

    /** Products keep pointing at an archived category; it just can't be assigned anymore. */
    public void archive(UUID id) {
        load(id).archive(Instant.now());
    }

    public CategoryResponse restore(UUID id) {
        ProductCategory category = load(id);
        category.restore();
        return CategoryResponse.of(category, 0);
    }

    @Transactional(readOnly = true)
    public ProductCategory requireActive(UUID id) {
        ProductCategory category = categories.findById(id)
                .orElseThrow(() -> ApiException.badRequest("Category '" + id + "' does not exist"));
        if (category.isArchived()) {
            throw ApiException.conflict("Category '" + category.getName() + "' is archived and can't be used");
        }
        return category;
    }

    private ProductCategory load(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category", id));
    }
}
