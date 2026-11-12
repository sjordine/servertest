package com.servertest.products.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.servertest.products.category.ProductCategory;
import com.servertest.products.common.BaseEntity;
import com.servertest.products.common.Refs.ProductRef;
import com.servertest.products.packaging.ProductPackage;
import com.servertest.products.unit.MeasurementUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * One specific sellable item, identified by its SKU. Different sizes are
 * different products (a 1 l and a 0.5 l bottle), tied together by a category.
 */
@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    /** Business key: unique, upper-case, never changed and never reused. */
    @Column(nullable = false, length = 64, updatable = false)
    private String sku;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    /** Optional net content of one item, e.g. 1 l. Both set or both null. */
    @Column(name = "content_quantity", precision = 14, scale = 3)
    private BigDecimal contentQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_unit_id")
    private MeasurementUnit contentUnit;

    @OneToMany(mappedBy = "product")
    @OrderBy("code ASC")
    private List<ProductPackage> packages = new ArrayList<>();

    protected Product() {}

    public Product(String sku, String description) {
        this.sku = sku;
        this.description = description;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public BigDecimal getContentQuantity() {
        return contentQuantity;
    }

    public MeasurementUnit getContentUnit() {
        return contentUnit;
    }

    public void setContent(BigDecimal quantity, MeasurementUnit unit) {
        this.contentQuantity = quantity;
        this.contentUnit = unit;
    }

    public List<ProductPackage> getPackages() {
        return packages;
    }

    /** Archives the product and its active packages with the same timestamp, so restore can undo exactly this. */
    @Override
    public void archive(Instant at) {
        if (isArchived()) {
            return;
        }
        super.archive(at);
        packages.forEach(p -> p.archive(at));
    }

    /** Restores the product and the packages that were archived together with it. */
    @Override
    public void restore() {
        Instant at = getArchivedAt();
        if (at == null) {
            return;
        }
        packages.stream().filter(p -> at.equals(p.getArchivedAt())).forEach(ProductPackage::restore);
        super.restore();
    }

    public ProductRef toRef() {
        return new ProductRef(getId(), sku, description, isArchived());
    }
}
