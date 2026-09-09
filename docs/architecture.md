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
`TransferUseCase`, `DeleteTransactionsUseCase`, `StatisticsUseCase`, and `MerchantRuleService`.
Controllers stay thin and delegate to these.

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
are excluded from every statistic; they carry the reserved **Transfer** category (system rows,
non-deletable) so they appear and filter as a normal group in the UI.

Statistics: `GET /api/v1/statistics/summary?from&to[&accountId|kind][&topN][&granularity]` reports
period totals (income, expense magnitude, net = income − expense, transaction count, uncategorized
count, average daily expense) plus the same split by month, by category (uncategorized bucketed as
`(uncategorized)`), top merchants, and a `trend` of time buckets. The optional `granularity`
(`day|week|month|quarter|year`, default `month`) selects the bucket width of the `trend`; each
trend point carries its inclusive `start`/`end` dates. Amounts are summed in the base currency from
`base_amount`; rows lacking a base amount (foreign currency without an import-time rate) are counted
in an `unconverted` field rather than the money buckets. `kind` restricts to accounts tagged
`PERSONAL`/`BUSINESS` (the informal business profit/loss view).

Merchant defaults: `GET/POST /api/v1/merchant-rules`, `DELETE /api/v1/merchant-rules/{id}`,
`POST /api/v1/merchant-rules/{id}/apply` (one rule), and `POST /api/v1/merchant-rules/apply`
(all rules). A rule maps a counterparty (matched exactly, case- and spacing-insensitively) to a
category; statement imports auto-tag matching fresh rows, and the apply actions tag the
already-imported uncategorized rows of that counterparty on demand. Deleting a category removes
its rules.

Statements: `POST /api/v1/statements` (multipart import for one account),
`GET /api/v1/statements` (list, newest first, each with the count of stored transactions it
introduced), and `DELETE /api/v1/statements/{id}` (removes the statement and the transaction rows
it introduced, un-pairing any surviving transfer leg, so its file can be re-imported). Original
documents are not stored — only their metadata and the parsed, deduplicated transactions.
