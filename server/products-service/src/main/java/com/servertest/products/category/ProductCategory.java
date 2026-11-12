package com.servertest.products.category;

import com.servertest.products.common.BaseEntity;
import com.servertest.products.common.Refs.CategoryRef;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Groups related products, e.g. "Whole milk" groups the 1 l and the 0.5 l bottle. */
@Entity
@Table(name = "product_category")
public class ProductCategory extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    protected ProductCategory() {}

    public ProductCategory(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CategoryRef toRef() {
        return new CategoryRef(getId(), name);
    }
}
