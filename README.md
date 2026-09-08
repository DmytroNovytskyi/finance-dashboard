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
                    │  PostgreSQL 16              │
                    │  data/  (bind mount)        │
                    └─────────────────────────────┘
```

- The **frontend** serves the built app and proxies `/api` to the backend.
- The **backend** imports bank statements (per-bank parsers), converts foreign-currency
  amounts to a configurable base currency using official NBP rates, and answers statistics.
- **PostgreSQL** persists everything; its data directory is a bind mount on the deploy host.

## Tech stack

| Concern   | Choice                              |
|-----------|-------------------------------------|
| Backend   | Java 21, Spring Boot 3, Gradle      |
| Persistence | Spring Data JPA, Flyway migrations |
| Frontend  | Vite + React + TypeScript, MUI      |
| Database  | PostgreSQL 16                       |
| FX rates  | NBP (api.nbp.pl), DB-first          |

## Prerequisites

- Deployment: Docker with the Compose plugin.
- Local development: JDK 21 and Node 20+ (Docker only for the database).

## Deploy

See [docs/deployment.md](docs/deployment.md) for the full layout and steps. In short, on a
host with the repo cloned into a deploy directory:

```bash
cp deploy/docker-compose.yml ./docker-compose.yml
cp .env.example .env     # then fill in DB_USER / DB_PASSWORD
mkdir data
docker compose up -d
```

The Compose file, the `.env` secrets, and the `data/` directory all live outside the
repository.

## Local development

Start the database, then the two dev processes:

```bash
docker compose up -d db            # needs a Compose file wiring the data/ dir
cd backend && ./gradlew bootRun    # API on http://localhost:8080
cd frontend && npm run dev         # dev server on http://localhost:5173, proxies /api
```

## API docs

When the backend runs, OpenAPI / Swagger UI is available at
http://localhost:8080/swagger-ui.html.

## How to import a statement

1. Create an account (name + currency) via the UI or `POST /api/v1/accounts`.
2. On the Import page choose that account and upload the bank statement PDF.
3. Transactions are stored, foreign amounts are converted to the base currency, and the
   statement is filed. Re-uploading the same file is a no-op.

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
