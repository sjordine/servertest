package com.servertest.products.unit;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UnitDtos {

    private UnitDtos() {}

    public record UnitRequest(
            @NotBlank @Size(max = 16) String code,
            @NotBlank @Size(max = 64) String name) {}

    public record UnitResponse(UUID id, String code, String name, boolean archived) {

        static UnitResponse of(MeasurementUnit u) {
            return new UnitResponse(u.getId(), u.getCode(), u.getName(), u.isArchived());
        }
    }
}
