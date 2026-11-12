-- @conn products_db (products)
-- Put the cursor on a query and press Cmd+E Cmd+E to run it.
-- Or select several lines first and press Cmd+E Cmd+E to run just those.

-- Active catalogue: product, category, content and each package
SELECT c.name                      AS category,
       p.sku,
       p.description,
       p.content_quantity || ' ' || cu.code AS content,
       pk.code                     AS package_code,
       pk.quantity || ' ' || u.code AS package
FROM product p
LEFT JOIN product_category c  ON c.id = p.category_id
LEFT JOIN measurement_unit cu ON cu.id = p.content_unit_id
LEFT JOIN product_package pk  ON pk.product_id = p.id AND pk.archived_at IS NULL
LEFT JOIN measurement_unit u  ON u.id = pk.unit_id
WHERE p.archived_at IS NULL
ORDER BY c.name NULLS LAST, p.sku, pk.code;

-- Package by code (what the stock service will reference), including archived ones
SELECT pk.code, pk.quantity, u.code AS unit, p.sku, pk.archived_at
FROM product_package pk
JOIN product p          ON p.id = pk.product_id
JOIN measurement_unit u ON u.id = pk.unit_id
WHERE pk.code = 'MILK-WH-1L-6UN';

-- Archived products and packages
SELECT 'product' AS kind, sku AS key, archived_at FROM product WHERE archived_at IS NOT NULL
UNION ALL
SELECT 'package', code, archived_at FROM product_package WHERE archived_at IS NOT NULL
ORDER BY archived_at DESC;

-- Units and categories
SELECT * FROM measurement_unit ORDER BY code;
SELECT * FROM product_category ORDER BY name;

-- Applied schema migrations (Flyway)
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;

-- Tables in this database
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';

-- Hello-world row used by GET /api/products/hello
SELECT * FROM greeting;
