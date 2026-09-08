# Finance Dashboard — Project Notes

## What this is

A personal expense dashboard: import bank statement files, parse them into a normalized
transaction model, categorize spending, and view statistics. Backend is Java 21 +
Spring Boot 3 (Gradle), frontend is Vite + React + TypeScript (MUI), DB is PostgreSQL 18.
`docs/` is the source of architecture truth — keep it current when the system changes.

## Build / run / test

- Backend tests + build: `cd backend && ./gradlew test`, `./gradlew build`
- Backend coverage report: `./gradlew jacocoTestReport` (threshold enforced)
- Backend run: `./gradlew bootRun` (http://localhost:8080)
- Frontend dev: `cd frontend && npm install && npm run dev`
- DB (local dev): external Compose file wiring the `data/` directory
- Deploy: see `docs/deployment.md` (clone → copy `deploy/` → fill `.env` → `docker compose up -d`)

## Architecture overview

Hexagonal-lite, layered by package under `com.financedashboard`:

- `domain` — pure domain + outbound port interfaces, no Spring/framework deps
- `application` — use cases that orchestrate the ports
- `infrastructure` — JPA persistence, bank/format parsers + registry, NBP FX client
- `web` — thin REST controllers, DTOs, MapStruct mappers, exception handling

REST API base path is `/api/v1`. All source lives in `backend/src/main/java`,
`backend/src/main/resources` (single Gradle module).

## Conventions

- Self-documenting code: Javadoc on all public types/methods in `domain`, `port`, and
  `application`; each parser documents the bank, the exact layout it handles, and a sample.
- Design patterns in use: Strategy (parsers), Registry/Factory, Chain of Responsibility
  (parser selection), Repository, MapStruct mappers, Facade (use cases), Adapter (NBP).
- Doc comments on declarations only — no inline comments in code bodies.
- All project artifacts in English; chat may be in Russian.
- Amounts are signed (expense negative, income positive). Internal transfers are
  `nature=TRANSFER` and are excluded from all statistics.

## Testing requirement

Java code is covered by tests: unit (JUnit 5 + AssertJ) for domain/use cases/parsers/FX,
integration (`@SpringBootTest` + Testcontainers Postgres) for persistence and the REST API,
WireMock for the external NBP HTTP. JaCoCo enforces a line-coverage threshold.

## Secrets rule

Never commit secrets. `.env` lives on the deploy host outside the repo; `local/` is a
development-only workspace and is never committed (its contents hold no deployment
instructions).

## Layout reference

- `docs/` — architecture, data model, parser runbook, FX rate rules, deployment
- `deploy/` — committed Compose template, no secrets
- `local/` — dev workspace (ignored)
