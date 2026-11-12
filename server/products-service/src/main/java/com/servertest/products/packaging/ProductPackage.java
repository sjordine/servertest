package com.servertest.products.packaging;

import java.math.BigDecimal;

import com.servertest.products.common.BaseEntity;
import com.servertest.products.product.Product;
import com.servertest.products.unit.MeasurementUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A way a product is packed, e.g. a single bottle (1 un) or a box of 6 (6 un).
 * Identified by its code, which is what stock is counted against. The code,
 * product and measure are immutable: changing them would rewrite history, so
 * archive the package and create a new one instead.
 */
@Entity
@Table(name = "product_package")
public class ProductPackage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false, updatable = false)
    private MeasurementUnit unit;

    @Column(nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantity;

    protected ProductPackage() {}

    public ProductPackage(Product product, String code, String description, MeasurementUnit unit, BigDecimal quantity) {
        this.product = product;
        this.code = code;
        this.description = description;
        this.unit = unit;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
