package com.servertest.products.unit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.servertest.products.common.ApiException;
import com.servertest.products.common.Keys;
import com.servertest.products.unit.UnitDtos.UnitRequest;
import com.servertest.products.unit.UnitDtos.UnitResponse;

@Service
@Transactional
public class UnitService {

    private final MeasurementUnitRepository units;

    public UnitService(MeasurementUnitRepository units) {
        this.units = units;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> list(boolean includeArchived) {
        var rows = includeArchived ? units.findAllByOrderByCodeAsc() : units.findAllByArchivedAtIsNullOrderByCodeAsc();
        return rows.stream().map(UnitResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public UnitResponse get(UUID id) {
        return UnitResponse.of(load(id));
    }

    public UnitResponse create(UnitRequest req) {
        String code = Keys.unitCode(req.code());
        if (units.existsByCode(code)) {
            throw ApiException.conflict("Unit code '" + code + "' already exists");
        }
        return UnitResponse.of(units.save(new MeasurementUnit(code, req.name().trim())));
    }

    public UnitResponse update(UUID id, UnitRequest req) {
        MeasurementUnit unit = load(id);
        String code = Keys.unitCode(req.code());
        if (units.existsByCodeAndIdNot(code, id)) {
            throw ApiException.conflict("Unit code '" + code + "' already exists");
        }
        unit.setCode(code);
        unit.setName(req.name().trim());
        return UnitResponse.of(unit);
    }

    /** Archived units stay valid for existing packages but can't be used for new ones. */
    public void archive(UUID id) {
        load(id).archive(Instant.now());
    }

    public UnitResponse restore(UUID id) {
        MeasurementUnit unit = load(id);
        unit.restore();
        return UnitResponse.of(unit);
    }

    /** Loads a unit that may be referenced by new data: must exist and be active. */
    @Transactional(readOnly = true)
    public MeasurementUnit requireActive(UUID id) {
        MeasurementUnit unit = units.findById(id)
                .orElseThrow(() -> ApiException.badRequest("Unit '" + id + "' does not exist"));
        if (unit.isArchived()) {
            throw ApiException.conflict("Unit '" + unit.getCode() + "' is archived and can't be used");
        }
        return unit;
    }

    private MeasurementUnit load(UUID id) {
        return units.findById(id).orElseThrow(() -> ApiException.notFound("Unit", id));
    }
}
