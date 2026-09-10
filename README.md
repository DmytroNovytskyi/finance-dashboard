# Finance Dashboard

A personal expense dashboard: import bank statements, parse them into a normalized
transaction model, categorize spending into groups, and view statistics that show where
money goes.

## Architecture

```
                    ┌─────────────────────────────┐
                    │  frontend (nginx, :8100)    │
                    │  React + Vite + TS + MUI    │
                    └──────────────┬──────────────┘
                                   │  /api  (reverse proxy)
                    ┌──────────────▼──────────────┐
                    │  backend  (Spring Boot 3)   │
                    │  Java 21, Gradle            │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  PostgreSQL 18              │
                    │  data/  (bind mount)        │
                    └─────────────────────────────┘
```

- The **frontend** serves the built app and proxies `/api` to the backend.
- The **backend** imports bank statements (per-bank parsers) and values every transaction in
  each supported currency at its own date using official NBP rates, then answers statistics.
- **PostgreSQL** persists everything; its data directory is a bind mount on the deploy host.

## Tech stack

| Concern   | Choice                              |
|-----------|-------------------------------------|
| Backend   | Java 21, Spring Boot 3, Gradle      |
| Persistence | Spring Data JPA, Flyway migrations |
| Frontend  | Vite + React + TypeScript, MUI      |
| Database  | PostgreSQL 18                       |
| FX rates  | NBP (api.nbp.pl), DB-first          |

## Prerequisites

- Deployment: Docker with the Compose plugin.
- Local development: JDK 21, Node 20+, and Podman for the database.

## Deploy

See [docs/deployment.md](docs/deployment.md) for the full layout and steps. In short, on a
deploy directory outside the repo:

```bash
git clone <repo-url> app
cp app/deploy/docker-compose.yml ./docker-compose.yml
cp app/.env.example .env     # then fill in DB_USER / DB_PASSWORD
mkdir data                   # must be empty on the first run
docker compose up -d --build
```

The Compose file, the `.env` secrets, and the `data/` directory all live outside the
repository. The UI is published on `:8100` (`FRONTEND_PORT`); the database and the API publish
no ports and are reachable only from the Compose network.

To update a running deployment, pull in `app/` and rebuild:

```bash
cd app && git pull
cd .. && docker compose up -d --build
```

## Local development

Start the database, then the two dev processes:

```bash
podman start finance-db            # Postgres 18 on :5432,
cd backend && ./gradlew bootRun    # API on http://localhost:8080
cd frontend && npm run dev         # dev server on http://localhost:5173, proxies /api
```

The `finance-db` container is created once from the external Compose file that wires the
`data/` directory; after that it is started per session as shown above. Podman's machine must
be running first.

## API docs

When the backend runs, OpenAPI / Swagger UI is available at
http://localhost:8080/swagger-ui.html.

## How to import a statement

1. On the Import page upload one or more bank statement PDFs. The owning account is read from
   each file — reused when it already exists, created automatically otherwise.
2. Transactions are stored, each valued in every supported currency at its own date, and the
   statement is filed. Re-uploading the same file is a no-op.
3. Switch the display currency on the dashboard to read the same transactions in another
   currency.

## Project layout

```
backend/    Gradle application (single module)
frontend/   Vite + React + TypeScript
docs/       evergreen reference (architecture, data model, parsers, FX, deployment)
deploy/     committed Compose template (placed outside the repo on the host)
local/      development-only workspace (not committed)
```

## License

Proprietary.
