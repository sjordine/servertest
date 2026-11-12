package com.servertest.products.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.servertest.products.common.Keys;
import com.servertest.products.common.Refs.CategoryRef;
import com.servertest.products.common.Refs.UnitRef;
import com.servertest.products.packaging.PackageDtos.PackageResponse;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class ProductDtos {

    private ProductDtos() {}

    public record ProductCreateRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = Keys.CODE_PATTERN, message = "use letters, digits, '.', '_' or '-'") String sku,
            @NotBlank @Size(max = 2000) String description,
            UUID categoryId,
            @Positive @Digits(integer = 11, fraction = 3) BigDecimal contentQuantity,
            UUID contentUnitId) {}

    /** The SKU is immutable, so it is not part of an update. */
    public record ProductUpdateRequest(
            @NotBlank @Size(max = 2000) String description,
            UUID categoryId,
            @Positive @Digits(integer = 11, fraction = 3) BigDecimal contentQuantity,
            UUID contentUnitId) {}

    public record ContentResponse(BigDecimal quantity, UnitRef unit, String label) {

        static ContentResponse of(Product p) {
            if (p.getContentQuantity() == null) {
                return null;
            }
            return new ContentResponse(p.getContentQuantity().stripTrailingZeros(), p.getContentUnit().toRef(),
                    Keys.label(p.getContentQuantity(), p.getContentUnit().getCode()));
        }
    }

    public record ProductSummary(UUID id, String sku, String description, CategoryRef category,
            ContentResponse content, long packageCount, boolean archived) {

        static ProductSummary of(Product p, long packageCount) {
            return new ProductSummary(p.getId(), p.getSku(), p.getDescription(),
                    p.getCategory() == null ? null : p.getCategory().toRef(),
                    ContentResponse.of(p), packageCount, p.isArchived());
        }
    }

    public record ProductDetail(UUID id, String sku, String description, CategoryRef category,
            ContentResponse content, boolean archived, Instant createdAt, Instant updatedAt, Instant archivedAt,
            List<PackageResponse> packages) {

        static ProductDetail of(Product p) {
            return new ProductDetail(p.getId(), p.getSku(), p.getDescription(),
                    p.getCategory() == null ? null : p.getCategory().toRef(),
                    ContentResponse.of(p), p.isArchived(), p.getCreatedAt(), p.getUpdatedAt(), p.getArchivedAt(),
                    p.getPackages().stream().map(PackageResponse::of).toList());
        }
    }
}
