package com.servertest.products.unit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, UUID> {

    List<MeasurementUnit> findAllByOrderByCodeAsc();

    List<MeasurementUnit> findAllByArchivedAtIsNullOrderByCodeAsc();

    boolean existsByCodeAndIdNot(String code, UUID id);

    boolean existsByCode(String code);
}
