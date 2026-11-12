package com.servertest.products.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.servertest.products.category.CategoryService;
import com.servertest.products.common.ApiException;
import com.servertest.products.common.Keys;
import com.servertest.products.common.PageResponse;
import com.servertest.products.packaging.ProductPackageRepository;
import com.servertest.products.product.ProductDtos.ProductCreateRequest;
import com.servertest.products.product.ProductDtos.ProductDetail;
import com.servertest.products.product.ProductDtos.ProductSummary;
import com.servertest.products.product.ProductDtos.ProductUpdateRequest;
import com.servertest.products.unit.MeasurementUnit;
import com.servertest.products.unit.UnitService;

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional
public class ProductService {

    static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository products;
    private final ProductPackageRepository packages;
    private final CategoryService categories;
    private final UnitService units;

    public ProductService(ProductRepository products, ProductPackageRepository packages,
            CategoryService categories, UnitService units) {
        this.products = products;
        this.packages = packages;
        this.categories = categories;
        this.units = units;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> list(String q, UUID categoryId, boolean includeArchived, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (!includeArchived) {
                where.add(cb.isNull(root.get("archivedAt")));
            }
            if (categoryId != null) {
                where.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            String term = Keys.trimToNull(q);
            if (term != null) {
                String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("sku")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("sku"));
        Page<Product> result = products.findAll(spec, pageable);

        Map<UUID, Long> counts = new HashMap<>();
        List<UUID> ids = result.getContent().stream().map(Product::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : packages.countActiveByProductIds(ids)) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }
        return PageResponse.of(result, p -> ProductSummary.of(p, counts.getOrDefault(p.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public ProductDetail get(UUID id) {
        return ProductDetail.of(load(id));
    }

    @Transactional(readOnly = true)
    public ProductDetail getBySku(String sku) {
        String key = Keys.code(sku);
        return ProductDetail.of(products.findBySku(key).orElseThrow(() -> ApiException.notFound("Product", key)));
    }

    public ProductDetail create(ProductCreateRequest req) {
        String sku = Keys.code(req.sku());
        if (products.existsBySku(sku)) {
            // Includes archived products: a SKU is never reused, so history stays unambiguous.
            throw ApiException.conflict("SKU '" + sku + "' already exists");
        }
        Product product = new Product(sku, req.description().trim());
        apply(product, req.categoryId(), req.contentQuantity(), req.contentUnitId());
        return ProductDetail.of(products.save(product));
    }

    public ProductDetail update(UUID id, ProductUpdateRequest req) {
        Product product = load(id);
        if (product.isArchived()) {
            throw ApiException.conflict("Product '" + product.getSku() + "' is archived; restore it before editing");
        }
        product.setDescription(req.description().trim());
        apply(product, req.categoryId(), req.contentQuantity(), req.contentUnitId());
        return ProductDetail.of(product);
    }

    /** Archives the product and all its active packages. */
    public void archive(UUID id) {
        load(id).archive(Instant.now());
    }

    /** Restores the product and the packages that were archived along with it. */
    public ProductDetail restore(UUID id) {
        Product product = load(id);
        product.restore();
        return ProductDetail.of(product);
    }

    private void apply(Product product, UUID categoryId, BigDecimal contentQuantity, UUID contentUnitId) {
        boolean sameCategory = product.getCategory() != null && product.getCategory().getId().equals(categoryId);
        if (categoryId == null) {
            product.setCategory(null);
        } else if (!sameCategory) {
            product.setCategory(categories.requireActive(categoryId));
        }

        if ((contentQuantity == null) != (contentUnitId == null)) {
            throw ApiException.badRequest("Content needs both a quantity and a unit, or neither");
        }
        if (contentUnitId == null) {
            product.setContent(null, null);
        } else {
            boolean sameUnit = product.getContentUnit() != null && product.getContentUnit().getId().equals(contentUnitId);
            MeasurementUnit unit = sameUnit ? product.getContentUnit() : units.requireActive(contentUnitId);
            product.setContent(contentQuantity, unit);
        }
    }

    private Product load(UUID id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product", id));
    }
}
