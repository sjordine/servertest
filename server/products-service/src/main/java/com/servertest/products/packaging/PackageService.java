package com.servertest.products.packaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.servertest.products.common.ApiException;
import com.servertest.products.common.Keys;
import com.servertest.products.packaging.PackageDtos.PackageCreateRequest;
import com.servertest.products.packaging.PackageDtos.PackageResponse;
import com.servertest.products.packaging.PackageDtos.PackageUpdateRequest;
import com.servertest.products.product.Product;
import com.servertest.products.product.ProductRepository;
import com.servertest.products.unit.MeasurementUnit;
import com.servertest.products.unit.UnitService;

@Service
@Transactional
public class PackageService {

    static final int CODE_MAX = 64;

    private final ProductPackageRepository packages;
    private final ProductRepository products;
    private final UnitService units;

    public PackageService(ProductPackageRepository packages, ProductRepository products, UnitService units) {
        this.packages = packages;
        this.products = products;
        this.units = units;
    }

    @Transactional(readOnly = true)
    public List<PackageResponse> list(UUID productId, boolean includeArchived) {
        loadProduct(productId);
        var rows = includeArchived ? packages.findByProductIdOrderByCodeAsc(productId)
                : packages.findByProductIdAndArchivedAtIsNullOrderByCodeAsc(productId);
        return rows.stream().map(PackageResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public PackageResponse get(UUID productId, UUID packageId) {
        return PackageResponse.of(load(productId, packageId));
    }

    /** Lookup by business key; archived packages are returned too (flagged), so history resolves. */
    @Transactional(readOnly = true)
    public PackageResponse getByCode(String code) {
        String key = Keys.code(code);
        return PackageResponse.of(packages.findByCode(key).orElseThrow(() -> ApiException.notFound("Package", key)));
    }

    public PackageResponse create(UUID productId, PackageCreateRequest req) {
        Product product = loadProduct(productId);
        if (product.isArchived()) {
            throw ApiException.conflict("Product '" + product.getSku() + "' is archived; packages can't be added");
        }
        MeasurementUnit unit = units.requireActive(req.unitId());
        BigDecimal quantity = req.quantity();
        ensureMeasureFree(product, unit, quantity, null);

        String requested = Keys.trimToNull(req.code());
        String code;
        if (requested != null) {
            code = Keys.code(requested);
            if (packages.existsByCode(code)) {
                // Includes archived packages: a code is never reused.
                throw ApiException.conflict("Package code '" + code + "' already exists");
            }
        } else {
            code = generateCode(product, unit, quantity);
        }

        ProductPackage pkg = new ProductPackage(product, code, Keys.trimToNull(req.description()), unit, quantity);
        product.getPackages().add(pkg);
        return PackageResponse.of(packages.save(pkg));
    }

    public PackageResponse update(UUID productId, UUID packageId, PackageUpdateRequest req) {
        ProductPackage pkg = load(productId, packageId);
        if (pkg.isArchived()) {
            throw ApiException.conflict("Package '" + pkg.getCode() + "' is archived; restore it before editing");
        }
        pkg.setDescription(Keys.trimToNull(req.description()));
        return PackageResponse.of(pkg);
    }

    public void archive(UUID productId, UUID packageId) {
        load(productId, packageId).archive(Instant.now());
    }

    public PackageResponse restore(UUID productId, UUID packageId) {
        ProductPackage pkg = load(productId, packageId);
        if (!pkg.isArchived()) {
            return PackageResponse.of(pkg);
        }
        if (pkg.getProduct().isArchived()) {
            throw ApiException.conflict("Product '" + pkg.getProduct().getSku() + "' is archived; restore it first");
        }
        ensureMeasureFree(pkg.getProduct(), pkg.getUnit(), pkg.getQuantity(), pkg.getId());
        pkg.restore();
        return PackageResponse.of(pkg);
    }

    /** {@code <SKU>-<QTY><UNIT>}, e.g. MILK-WH-1L-6UN; adds -2, -3... if that code was used before. */
    String generateCode(Product product, MeasurementUnit unit, BigDecimal quantity) {
        String base = Keys.code(product.getSku() + "-" + Keys.quantity(quantity) + unit.getCode());
        String code = base;
        for (int n = 2; packages.existsByCode(code); n++) {
            code = base + "-" + n;
        }
        if (code.length() > CODE_MAX) {
            throw ApiException.badRequest("Generated code '" + code + "' is longer than " + CODE_MAX
                    + " characters; provide a code");
        }
        return code;
    }

    private void ensureMeasureFree(Product product, MeasurementUnit unit, BigDecimal quantity, UUID excludeId) {
        if (packages.existsActiveMeasure(product.getId(), unit.getId(), quantity, excludeId)) {
            throw ApiException.conflict("Product '" + product.getSku() + "' already has an active package of "
                    + Keys.label(quantity, unit.getCode()));
        }
    }

    private Product loadProduct(UUID productId) {
        return products.findById(productId).orElseThrow(() -> ApiException.notFound("Product", productId));
    }

    private ProductPackage load(UUID productId, UUID packageId) {
        return packages.findById(packageId)
                .filter(p -> p.getProduct().getId().equals(productId))
                .orElseThrow(() -> ApiException.notFound("Package", packageId));
    }
}
