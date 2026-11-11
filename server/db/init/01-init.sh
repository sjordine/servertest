#!/bin/bash
# Runs once, on the first start of the postgres container (empty data volume).
# Creates one database + one user per service, and a hello-world `greeting` table in each.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
	CREATE USER ${PRODUCTS_DB_USER} WITH PASSWORD '${PRODUCTS_DB_PASSWORD}';
	CREATE DATABASE products_db OWNER ${PRODUCTS_DB_USER};
	REVOKE CONNECT ON DATABASE products_db FROM PUBLIC;

	CREATE USER ${STOCK_DB_USER} WITH PASSWORD '${STOCK_DB_PASSWORD}';
	CREATE DATABASE stock_db OWNER ${STOCK_DB_USER};
	REVOKE CONNECT ON DATABASE stock_db FROM PUBLIC;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$PRODUCTS_DB_USER" --dbname products_db <<-EOSQL
	CREATE TABLE greeting (id SERIAL PRIMARY KEY, message TEXT NOT NULL);
	INSERT INTO greeting (message) VALUES ('Hello from products_db');
EOSQL

psql -v ON_ERROR_STOP=1 --username "$STOCK_DB_USER" --dbname stock_db <<-EOSQL
	CREATE TABLE greeting (id SERIAL PRIMARY KEY, message TEXT NOT NULL);
	INSERT INTO greeting (message) VALUES ('Hello from stock_db');
EOSQL
