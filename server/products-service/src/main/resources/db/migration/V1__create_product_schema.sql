-- Product catalogue: categories group products; a product is one sellable item
-- (identified by SKU); a package is a way a product is packed (identified by code).
-- Rows are never physically deleted: archived_at IS NOT NULL means archived.

CREATE TABLE measurement_unit (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(16)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT uq_measurement_unit_code UNIQUE (code)
);

CREATE TABLE product_category (
    id          UUID          PRIMARY KEY,
    name        VARCHAR(128)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_product_category_name ON product_category (lower(name));

CREATE TABLE product (
    id               UUID           PRIMARY KEY,
    category_id      UUID           REFERENCES product_category (id) ON DELETE RESTRICT,
    sku              VARCHAR(64)    NOT NULL,
    description      TEXT           NOT NULL,
    content_quantity NUMERIC(14, 3),
    content_unit_id  UUID           REFERENCES measurement_unit (id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    archived_at      TIMESTAMPTZ,
    CONSTRAINT uq_product_sku UNIQUE (sku),
    CONSTRAINT ck_product_content_pair CHECK ((content_quantity IS NULL) = (content_unit_id IS NULL)),
    CONSTRAINT ck_product_content_positive CHECK (content_quantity IS NULL OR content_quantity > 0)
);
CREATE INDEX ix_product_category ON product (category_id);

CREATE TABLE product_package (
    id          UUID           PRIMARY KEY,
    product_id  UUID           NOT NULL REFERENCES product (id) ON DELETE RESTRICT,
    code        VARCHAR(64)    NOT NULL,
    description TEXT,
    unit_id     UUID           NOT NULL REFERENCES measurement_unit (id) ON DELETE RESTRICT,
    quantity    NUMERIC(14, 3) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT uq_product_package_code UNIQUE (code),
    CONSTRAINT ck_product_package_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX ix_product_package_product ON product_package (product_id);
CREATE INDEX ix_product_package_unit ON product_package (unit_id);
-- A product can't have two active packages with the same measure (e.g. two "6 un").
CREATE UNIQUE INDEX uq_product_package_active_measure
    ON product_package (product_id, unit_id, quantity)
    WHERE archived_at IS NULL;
