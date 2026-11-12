package com.servertest.products.unit;

import com.servertest.products.common.BaseEntity;
import com.servertest.products.common.Refs.UnitRef;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "measurement_unit")
public class MeasurementUnit extends BaseEntity {

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    protected MeasurementUnit() {}

    public MeasurementUnit(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UnitRef toRef() {
        return new UnitRef(getId(), code, name);
    }
}
