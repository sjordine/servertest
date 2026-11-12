# servertest
A Agentic Code testing with Docker

Client–server testbed: an iOS app and a React web app talk to a single Nginx gateway, which routes to microservices in Java (Spring Boot) and Go. Each service has its own Postgres database. Everything runs in Docker.

```
Browser / iOS ─▶ Nginx gateway :80 ─┬─ /api/products/* ─▶ products (Spring Boot, Java 17) ─▶ postgres/products_db
                                    ├─ /api/stock/*    ─▶ stock    (Go)                   ─▶ postgres/stock_db
                                    └─ /*              ─▶ web      (React + Vite)
```

## Layout
```
client/
  mobile/            iOS app (Xcode) – see client/mobile/README.md
  frontend/          React + Vite web app (runs in Docker)
server/
  gateway/           nginx.conf – routing rules
  products-service/  Spring Boot
  stock-service/     Go
  db/init/           creates the databases on first start
docker-compose.yml   the whole stack
```

## Run
Requires only Docker Desktop. No local Java, Go or Node is needed.

```bash
cp .env.example .env          # first time only
docker compose up --build -d  # build + start everything
```

- Web app: http://localhost (Products, Categories, Units; **Environment check** tab runs the hello-world checks)
- APIs: `curl localhost/api/products/hello` and `curl localhost/api/stock/hello`
- Postgres from your Mac: `localhost:5432` (credentials in `.env`)

In Docker Desktop → **Containers**, the stack appears as one group, `servertest`.

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
curl localhost/api/products/units                                   # seeded: un, kg, g, l, ml
curl -XPOST localhost/api/products/categories -H 'Content-Type: application/json' -d '{"name":"Whole milk"}'
curl -XPOST localhost/api/products -H 'Content-Type: application/json' \
     -d '{"sku":"MILK-WH-1L","description":"Whole milk 1 l bottle","categoryId":"<category id>","contentQuantity":1,"contentUnitId":"<l id>"}'
curl -XPOST localhost/api/products/<product id>/packages -H 'Content-Type: application/json' -d '{"unitId":"<un id>","quantity":6}'
curl 'localhost/api/products?q=milk'                                # paged list (q, categoryId, includeArchived, page, size)
curl localhost/api/products/sku/MILK-WH-1L                          # product + packages
curl localhost/api/products/packages/MILK-WH-1L-6UN                 # package by code
curl -XDELETE localhost/api/products/<product id>                   # archive
```

Errors come back as RFC 7807 `ProblemDetail`: 400 with an `errors` map for validation, 404, and 409 for duplicates or archived references.

Tests are integration tests. They run against the dev Postgres, in their own `products_test` schema, and roll back:
```bash
docker compose exec products mvn -B test
```

## Everyday commands
| What | Command |
|---|---|
| Status | `docker compose ps` |
| Logs (all / one) | `docker compose logs -f` / `docker compose logs -f products` |
| Stop | `docker compose down` |
| Stop + wipe DB | `docker compose down -v` (init script runs again on next start) |
| Shell into a container | `docker compose exec stock sh` |
| psql | `docker compose exec postgres psql -U products -d products_db` |
| Reload gateway config | `docker compose exec gateway nginx -s reload` |
| After changing pom.xml / go.mod / package.json | `docker compose up --build -d -V` |

## Hot reload
Your source folders are bind-mounted into the containers, so you edit on your Mac and the container picks up the change:
- **Go (stock):** `air` rebuilds in about 1–2s.
- **Java (products):** files under `src/` are recompiled and spring-boot-devtools restarts the app in a few seconds.
- **React (web):** Vite HMR updates the browser instantly.

## Production images
Each Dockerfile also has a `prod` target (a JRE-only image, a distroless Go binary, and Nginx serving the built frontend). The dev stack doesn't use these.
