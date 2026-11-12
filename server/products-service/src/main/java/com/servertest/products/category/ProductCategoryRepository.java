package com.servertest.products.category;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    List<ProductCategory> findAllByOrderByNameAsc();

    List<ProductCategory> findAllByArchivedAtIsNullOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("select p.category.id, count(p) from Product p where p.category is not null and p.archivedAt is null group by p.category.id")
    List<Object[]> countActiveProductsByCategory();
}
