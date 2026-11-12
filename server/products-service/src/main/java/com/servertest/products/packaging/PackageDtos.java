package com.servertest.products.packaging;

import java.math.BigDecimal;
import java.util.UUID;

import com.servertest.products.common.Keys;
import com.servertest.products.common.Refs.ProductRef;
import com.servertest.products.common.Refs.UnitRef;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class PackageDtos {

    private PackageDtos() {}

    /** {@code code} is optional: when blank, {@code <SKU>-<QTY><UNIT>} is generated (e.g. MILK-WH-1L-6UN). */
    public record PackageCreateRequest(
            @Size(max = 64)
            @Pattern(regexp = "^$|" + Keys.CODE_PATTERN, message = "use letters, digits, '.', '_' or '-'") String code,
            @Size(max = 2000) String description,
            @NotNull UUID unitId,
            @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity) {}

    /** Code and measure are immutable; only the description can change. */
    public record PackageUpdateRequest(@Size(max = 2000) String description) {}

    public record PackageResponse(UUID id, String code, String description, BigDecimal quantity, UnitRef unit,
            String label, ProductRef product, boolean archived) {

        public static PackageResponse of(ProductPackage p) {
            return new PackageResponse(p.getId(), p.getCode(), p.getDescription(),
                    p.getQuantity().stripTrailingZeros(), p.getUnit().toRef(),
                    Keys.label(p.getQuantity(), p.getUnit().getCode()), p.getProduct().toRef(), p.isArchived());
        }
    }
}
