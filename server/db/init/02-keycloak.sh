#!/bin/bash
# DEV ONLY: hosts Keycloak's database on the shared dev Postgres to keep the dev stack light.
# Keycloak only knows its DB through KEYCLOAK_DB_URL / _USER / _PASSWORD (.env), so in
# production you point those at a separate Postgres (or a managed one) and drop this script.
#
# Runs once, on the first start of the postgres container (empty data volume).
# Isolation: own user (no superuser), own database, CONNECT revoked from PUBLIC,
# and no service user is granted access to it.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
	CREATE USER ${KEYCLOAK_DB_USER} WITH PASSWORD '${KEYCLOAK_DB_PASSWORD}';
	CREATE DATABASE keycloak_db OWNER ${KEYCLOAK_DB_USER};
	REVOKE CONNECT ON DATABASE keycloak_db FROM PUBLIC;
EOSQL
