package com.servertest.products.packaging;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductPackageRepository extends JpaRepository<ProductPackage, UUID> {

    @EntityGraph(attributePaths = {"unit"})
    List<ProductPackage> findByProductIdOrderByCodeAsc(UUID productId);

    @EntityGraph(attributePaths = {"unit"})
    List<ProductPackage> findByProductIdAndArchivedAtIsNullOrderByCodeAsc(UUID productId);

    @EntityGraph(attributePaths = {"unit", "product"})
    Optional<ProductPackage> findByCode(String code);

    boolean existsByCode(String code);

    /** Active package with the same measure on the same product (other than {@code excludeId}). */
    @Query("""
            select count(p) > 0 from ProductPackage p
            where p.product.id = :productId and p.unit.id = :unitId and p.quantity = :quantity
              and p.archivedAt is null and (:excludeId is null or p.id <> :excludeId)
            """)
    boolean existsActiveMeasure(@Param("productId") UUID productId, @Param("unitId") UUID unitId,
            @Param("quantity") BigDecimal quantity, @Param("excludeId") UUID excludeId);

    @Query("""
            select p.product.id, count(p) from ProductPackage p
            where p.product.id in :productIds and p.archivedAt is null
            group by p.product.id
            """)
    List<Object[]> countActiveByProductIds(@Param("productIds") Collection<UUID> productIds);
}
