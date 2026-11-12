# servertest
A Agentic Code testing with Docker

Client–server testbed: an iOS app and a React web app talk to a single Nginx gateway, which routes to microservices in Java (Spring Boot) and Go. Each service has its own Postgres database. Keycloak handles login and issues the tokens every service checks. Everything runs in Docker.

```
Browser / iOS ─▶ Nginx gateway :80 ─┬─ /api/products/* ─▶ products (Spring Boot, Java 17) ─▶ postgres/products_db
                                    ├─ /api/stock/*    ─▶ stock    (Go)                   ─▶ postgres/stock_db
                                    ├─ /auth/*         ─▶ keycloak (OIDC identity provider) ─▶ postgres/keycloak_db
                                    └─ /*              ─▶ web      (React + Vite)
```

## Layout
```
client/
  mobile/            iOS app (Xcode) – see client/mobile/README.md
  frontend/          React + Vite web app (runs in Docker)
server/
  gateway/           nginx.conf – routing rules
  keycloak/realm/    servertest-realm.json – clients, scopes, roles, dev users (imported on first start)
  products-service/  Spring Boot
  stock-service/     Go
  db/init/           creates the databases on first start (02-keycloak.sh: dev only)
docker-compose.yml   the whole stack
```

## Run
Requires only Docker Desktop. No local Java, Go or Node is needed.

```bash
cp .env.example .env          # first time only
docker compose up --build -d  # build + start everything
```

Upgrading a stack created before Keycloak was added:
1. Copy the new `KEYCLOAK_*` and `STOCK_CLIENT_SECRET` lines from `.env.example` into `.env`.
2. The Postgres volume already exists, so its init scripts won't run again. Either wipe it with `docker compose down -v`, or create Keycloak's database once:
   `docker compose exec postgres psql -U postgres -c "CREATE USER keycloak WITH PASSWORD 'keycloak'" -c "CREATE DATABASE keycloak_db OWNER keycloak" -c "REVOKE CONNECT ON DATABASE keycloak_db FROM PUBLIC"`
3. Run `docker compose up --build -d -V`.

- Web app: http://localhost. Sign in as `alice` / `alice` (editor) or `bob` / `bob` (read-only). The **Environment check** tab runs the hello-world checks and an end-to-end auth check.
- Public APIs: `curl localhost/api/products/hello` and `curl localhost/api/stock/hello`. Everything else needs a token (see [Authentication](#authentication)).
- Keycloak admin console: http://localhost/auth/admin (`admin` / `admin`)
- Postgres from your Mac: `localhost:5432` (credentials in `.env`)

In Docker Desktop → **Containers**, the stack appears as one group, `servertest`.

## Authentication

Login and permissions use **OpenID Connect / OAuth2**, with Keycloak as the identity provider.

- **Keycloak issues the tokens.** It runs in its own container. Users log in on Keycloak's pages, never in the app. Tokens are JWTs signed with Keycloak's private RSA key and are valid for 5 minutes.
- **Every service checks tokens itself.** It verifies the signature with Keycloak's public keys (JWKS, fetched once and cached), plus the issuer, the audience (the token must be for *this* service), the expiry, and the scope the endpoint needs. Keycloak is not called on each request, so this keeps working when services run on other hosts.
- **The gateway does not check tokens.** It only routes, so a request that bypasses it still gets checked by the service.

| Who | How they get a token | Keycloak client |
|---|---|---|
| Web app (browser) | Authorization Code + PKCE (`oidc-client-ts`). Tokens are kept in memory only. | `web` (public) |
| iOS app | Authorization Code + PKCE (e.g. AppAuth-iOS), see `client/mobile/README.md` | `ios` (public) |
| Another service (e.g. stock → products) | Client credentials: the service logs in as itself with its own secret | `stock-service` (confidential) |
| curl / scripts (**dev only**) | Password grant | `cli` (public) |

**Scopes** (what a token may do) and **audiences** (which service it is for):

| Scope | Audience | Allows | Granted to |
|---|---|---|---|
| `products:read` | `products-service` | `GET /api/products/**` | role `catalog-viewer` or `catalog-editor`; the `stock-service` client |
| `products:write` | `products-service` | `POST/PUT/PATCH/DELETE /api/products/**` | role `catalog-editor` |
| `stock:read` | `stock-service` | `GET /api/stock/auth-check` | any user |

Dev users: `alice` / `alice` (catalog-editor) and `bob` / `bob` (catalog-viewer). A missing or invalid token gets **401**, and a token without the needed scope gets **403**. Both come back as RFC 7807 `ProblemDetail` with a `WWW-Authenticate: Bearer …` header.

```bash
# User token (dev-only cli client)
TOKEN=$(curl -s localhost/auth/realms/servertest/protocol/openid-connect/token \
  -d grant_type=password -d client_id=cli -d username=alice -d password=alice \
  -d 'scope=openid products:read products:write stock:read' | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" localhost/api/products/units
curl -H "Authorization: Bearer $TOKEN" localhost/api/stock/auth-check   # user → stock → products

# Service token: what stock does internally (secret from .env)
curl -s localhost/auth/realms/servertest/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=stock-service -d client_secret=dev-stock-secret-change-me
```

**Issuer vs. internal URLs.** Tokens always carry `iss = ${KEYCLOAK_HOSTNAME}/realms/servertest` (`http://localhost/auth/...`), whichever URL they were requested from. Services fetch keys and tokens from the internal `http://keycloak:8080/auth/...` and only *compare* `iss`. These values are environment variables in `docker-compose.yml` / `.env`.

**Realm changes.** `server/keycloak/realm/servertest-realm.json` is imported only when the realm doesn't exist yet. After editing it, either change things in the admin console as well, or recreate the realm: delete it in the admin console, then run `docker compose restart keycloak`.

### Protecting a new service
1. In the realm JSON, add a client scope such as `orders:read`, with an audience mapper to `orders-service`. If the service calls others, also add a confidential client for it, with `serviceAccountsEnabled` and the scopes it needs as default scopes.
2. Give the service `OAUTH_ISSUER`, `OAUTH_JWKS_URI` and its audience. If it calls other services, also give it `OAUTH_TOKEN_URL`, `OAUTH_CLIENT_ID` and `OAUTH_CLIENT_SECRET`.
3. Validate tokens in the service:
   - **Spring Boot:** add `spring-boot-starter-oauth2-resource-server`, the `spring.security.oauth2.resourceserver.jwt` block (see products' `application.yml`) and a `SecurityConfig` like `products-service/.../common/SecurityConfig.java`.
   - **Go:** use the `auth` package in `stock-service/auth`: `verifier.Middleware("orders:read")` for incoming requests, and `auth.ServiceClientFromEnv` for calls to other services. It has nothing stock-specific in it, so move it into a shared module once a second Go service exists.

### When the system is distributed
Nothing in the code changes, only configuration:
- **Public URL and TLS:** serve the gateway over HTTPS and set `KEYCLOAK_HOSTNAME=https://your-domain/auth`. Add the new redirect URIs and web origins to the `web` client.
- **Service config:** set `OAUTH_JWKS_URI` and `OAUTH_TOKEN_URL` to wherever Keycloak is reachable from each service. That can be the public URL.
- **Keycloak itself:** run it with `start` (production mode) instead of `start-dev`, with 2+ replicas behind the load balancer. Or replace it with a managed OIDC provider (Auth0, Cognito, Entra ID…) by changing only these URLs.
- **Keycloak's database:** in dev it is a separate database and user (`keycloak_db`) on the shared Postgres. In production, give it its own Postgres instance or a managed one (RDS, Cloud SQL): set `KEYCLOAK_DB_URL` / `_USER` / `_PASSWORD`, and don't ship `server/db/init/02-keycloak.sh`. To keep existing users, run `pg_dump keycloak_db` and restore it into the new instance; otherwise the realm JSON recreates clients and scopes.
- **Secrets:** replace every dev secret (`STOCK_CLIENT_SECRET`, admin and DB passwords) and keep them in a secret manager. Remove the `cli` client and the dev users.
- **Optional hardening:** mTLS or a service mesh between services, and a BFF for the web app so tokens never reach the browser.

## Products service
Catalogue model (Flyway migrations in `server/products-service/src/main/resources/db/migration`):

```
product_category 1 ── 0..* product 1 ── 0..* product_package 0..* ── 1 measurement_unit
```

- **Product**: one sellable item, identified by a unique **SKU** (a 1 l and a 0.5 l bottle are different products). It can belong to a **category** and can have a content, e.g. `1 l`.
- **Package**: a way a product is packed (`1 un`, `6 un`, `10 kg`). It has a unique **code**, which stock is counted against. When no code is given, the code defaults to `<SKU>-<QTY><UNIT>`, e.g. `MILK-WH-1L-6UN`.
- Every table uses UUID primary keys. SKU and package code are indexed business keys. They are stored in upper case, can't be changed, and are never reused.
- Nothing is physically deleted. `DELETE` **archives** a record (`archived_at`). Archiving a product also archives its packages. Lists hide archived rows unless `includeArchived=true` is passed. Lookups by id, SKU and code still find archived rows, so history and reports keep working. `POST …/restore` undoes an archive.

```bash
# TOKEN: see Authentication above (alice can read and write)
AUTH="Authorization: Bearer $TOKEN"
curl -H "$AUTH" localhost/api/products/units                                   # seeded: un, kg, g, l, ml
curl -H "$AUTH" -XPOST localhost/api/products/categories -H 'Content-Type: application/json' -d '{"name":"Whole milk"}'
curl -H "$AUTH" -XPOST localhost/api/products -H 'Content-Type: application/json' \
     -d '{"sku":"MILK-WH-1L","description":"Whole milk 1 l bottle","categoryId":"<category id>","contentQuantity":1,"contentUnitId":"<l id>"}'
curl -H "$AUTH" -XPOST localhost/api/products/<product id>/packages -H 'Content-Type: application/json' -d '{"unitId":"<un id>","quantity":6}'
curl -H "$AUTH" 'localhost/api/products?q=milk'                                # paged list (q, categoryId, includeArchived, page, size)
curl -H "$AUTH" localhost/api/products/sku/MILK-WH-1L                          # product + packages
curl -H "$AUTH" localhost/api/products/packages/MILK-WH-1L-6UN                 # package by code
curl -H "$AUTH" -XDELETE localhost/api/products/<product id>                   # archive
```

Errors come back as RFC 7807 `ProblemDetail`: 400 with an `errors` map for validation, 404, and 409 for duplicates or archived references.

Tests are integration tests. They run against the dev Postgres, in their own `products_test` schema, and roll back. Tokens are mocked (`spring-security-test`), so Keycloak doesn't need to be running. `SecurityRulesTest` covers the 401/403 rules:
```bash
docker compose exec products mvn -B test
```

## Everyday commands
| What | Command |
|---|---|
| Status | `docker compose ps` |
| Logs (all / one) | `docker compose logs -f` / `docker compose logs -f products` |
| Stop | `docker compose down` |
| Stop + wipe DB | `docker compose down -v` (init scripts run again, and Keycloak re-imports the realm, on next start) |
| Shell into a container | `docker compose exec stock sh` |
| psql | `docker compose exec postgres psql -U products -d products_db` |
| Keycloak logs | `docker compose logs -f keycloak` |
| Reload gateway config | `docker compose exec gateway nginx -s reload` |
| After changing pom.xml / go.mod / package.json | `docker compose up --build -d -V` |

## Hot reload
Your source folders are bind-mounted into the containers, so you edit on your Mac and the container picks up the change:
- **Go (stock):** `air` rebuilds in about 1–2s.
- **Java (products):** files under `src/` are recompiled and spring-boot-devtools restarts the app in a few seconds.
- **React (web):** Vite HMR updates the browser instantly.

## Production images
Each Dockerfile also has a `prod` target (a JRE-only image, a distroless Go binary, and Nginx serving the built frontend with an SPA fallback). The dev stack doesn't use these.
