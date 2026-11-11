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

- Web app: http://localhost (click **Check all**)
- APIs: `curl localhost/api/products/hello` and `curl localhost/api/stock/hello`
- Postgres from your Mac: `localhost:5432` (credentials in `.env`)

In Docker Desktop → **Containers**, the stack appears as one group, `servertest`.

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
