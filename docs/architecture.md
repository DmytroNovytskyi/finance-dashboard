# Architecture

Three services: **PostgreSQL 18** (persistence), **backend** (Spring Boot 3 / Java 21,
Gradle), and **frontend** (Vite + React + TS, built to static assets and served by nginx).
The frontend proxies `/api` to the backend in production; in development Vite does the same.

## Backend layering (hexagonal-lite, by package)

Single Gradle module. All Java under `com.financedashboard`:

| Package | Responsibility |
|---|---|
| `domain` | Pure domain model (no Spring/framework dependencies) and **outbound port interfaces** |
| `application` | Use cases that orchestrate the ports; DTOs; typed exceptions |
| `infrastructure` | Adapters: JPA persistence, bank/format parsers + registry, NBP FX client |
| `web` | Thin REST controllers, request/response DTOs, MapStruct mappers, global exception handling, config |

Dependency direction is inward: `web → application → domain`. `infrastructure` implements
the `domain` ports; `application` depends only on `domain` ports and model.

## Ports

Defined in `domain/port`:

- `TransactionRepository`, `CategoryRepository`, `AccountRepository`,
  `BankStatementRepository` — persistence abstractions (implemented by Spring Data).
- `BankStatementParser` — Strategy; one implementation per bank/format, consumed by a
  registry in `infrastructure/parser`.
- `FxRateProvider` — resolves a currency→base rate for a date (DB-first, NBP fallback).

## Use cases

`application` exposes orchestration classes such as `ImportStatementUseCase`,
`CategorizeTransactionsUseCase`, `CategoryManagementUseCase`, `AccountManagementUseCase`,
`TransferUseCase`, `DeleteTransactionsUseCase`, `StatisticsUseCase`. Controllers stay thin
and delegate to these.

## Pattern summary

Strategy (parsers), Registry/Factory (parser selection), Chain of Responsibility
(`canParse` until a match), Repository (Spring Data JPA), Mapper (MapStruct), Builder
(`ParsedTransaction`), Facade (use cases), Adapter (NBP HTTP client behind `FxRateProvider`).

## API surface

Base path `/api/v1`, JSON, OpenAPI via springdoc at `/swagger-ui.html`. Endpoints are
listed in the plan's REST table; see `web/controller` for the authoritative list.

Accounts carry a user-managed `kind` (`PERSONAL` / `BUSINESS`, set via
`PATCH /api/v1/accounts/{id}`) that drives the statistics personal-vs-business split.
`nature=TRANSFER` rows (incl. auto-detected internal transfers between the user's own accounts)
are excluded from every statistic.
