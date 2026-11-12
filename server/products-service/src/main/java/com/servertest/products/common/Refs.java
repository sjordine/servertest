package com.servertest.products.common;

import java.util.UUID;

/** Small embedded references used inside responses. */
public final class Refs {

    private Refs() {}

    public record UnitRef(UUID id, String code, String name) {}

    public record CategoryRef(UUID id, String name) {}

    public record ProductRef(UUID id, String sku, String description, boolean archived) {}
}
