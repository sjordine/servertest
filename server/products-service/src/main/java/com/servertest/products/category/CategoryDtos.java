package com.servertest.products.category;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {}

    public record CategoryRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 2000) String description) {}

    public record CategoryResponse(UUID id, String name, String description, long productCount, boolean archived) {

        static CategoryResponse of(ProductCategory c, long productCount) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), productCount, c.isArchived());
        }
    }
}
