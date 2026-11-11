-- @conn products_db (products)
-- Put the cursor on a query and press Cmd+E Cmd+E to run it.
-- Or select several lines first and press Cmd+E Cmd+E to run just those.

SELECT * FROM greeting;

-- Tables in this database
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';

-- Try a write, then re-run the hello endpoint: curl localhost/api/products/hello
-- (it returns the row with the lowest id, so update id 1 to see the change)
-- UPDATE greeting SET message = 'Hello from products_db (edited in SQLTools)' WHERE id = 1;
