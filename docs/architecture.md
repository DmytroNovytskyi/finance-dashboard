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

Accounts are **created automatically** when a statement import names them (currency and
account number come from the file, name defaulted) and **removed automatically** once they hold
no transactions or statements. The user renames the account and tags its `kind` (`PERSONAL` /
`BUSINESS`) via `PATCH /api/v1/accounts/{id}`, which drives the statistics personal-vs-business
split; currency and the canonical account number are set by the statements and used to recognize
later imports of the same account.
`nature=TRANSFER` rows (incl. internal transfers between the user's own accounts)
are excluded from every statistic; they carry the reserved **Internal Transfer** category (system
rows, non-deletable) so they appear and filter as a normal group in the UI.

Internal-transfer detection and review: the matcher pairs non-transfer legs between own accounts
that reference each other's account number (mirror, works across currencies/FX) or that match in
amount/currency within 7 days. A pair whose **both legs carry a category** is treated as decided and
is not suggested; if any leg is uncategorized it stays a suggestion and both its legs are tagged
"Internal" in the UI. Import auto-applies a mirror pair whose legs are **both uncategorized**
(each leg becomes `TRANSFER` + Internal Transfer); a categorized leg is never auto-overridden.
Suggestions: `GET /api/v1/transfers/suggestions`, apply one pair `POST /api/v1/transfers`
(`{fromTransactionId,toTransactionId}`), apply all `POST /api/v1/transfers/suggestions/apply`,
and revert an applied transfer `POST /api/v1/transfers/{transactionId}/unlink` (both legs return
to their natural income/expense and category).

Transactions: `GET /api/v1/transactions` lists filtered rows (`accountId`, `categoryId`/`uncategorized`,
`nature`, `from`/`to`, `q`, `page`/`size`) ordered by `sort` (`date|amount|account|category`) with
`order` (`asc|desc`; default date descending). Account and category order by their names.

Statistics: `GET /api/v1/statistics/summary?from&to[&accountId|kind][&topN][&granularity][&displayCurrency]`
reports period totals (income, expense magnitude, net = income − expense, transaction count,
uncategorized count, average daily expense) plus the same split by month, by category
(uncategorized bucketed as `(uncategorized)`), top merchants, and a `trend` of time buckets. The
optional `granularity` (`day|week|month|quarter|year`, default `month`) selects the bucket width of
the `trend`; each trend point carries its inclusive `start`/`end` dates. Amounts are summed in the
currency selected by `displayCurrency` (default the base), reading the per-currency values stored
in `transaction_amount` at import time — there is no read-time FX and no "unconverted" bucket; a
row without a stored value for the requested currency is skipped. An unsupported `displayCurrency`
falls back to the base. `kind` restricts to accounts tagged `PERSONAL`/`BUSINESS` (the informal
business profit/loss view). Per-category series:
`GET /api/v1/statistics/categories/{categoryId}/transactions?from&to[&displayCurrency]` returns that
category's individual transactions as dated amounts in the requested currency in ascending date
order — one point per transaction, no bucketing — and
`GET /api/v1/statistics/categories/{categoryId}/trend?from&to[&granularity][&displayCurrency]`
returns the same income/expense per time bucket (`day|week|month|quarter|year`) for the bucket chart
views. Both accept the shared `displayCurrency` (selecting which stored per-transaction currency is
summed) and 404 for an unknown category.

Merchant defaults: `GET/POST /api/v1/merchant-rules`, `DELETE /api/v1/merchant-rules/{id}`,
`POST /api/v1/merchant-rules/{id}/unlink` (keeps the rule, reverts its rows),
`POST /api/v1/merchant-rules/{id}/apply` (one rule), and `POST /api/v1/merchant-rules/apply`
(all rules). A rule maps a counterparty (matched exactly, case- and spacing-insensitively) to a
category; statement imports auto-tag matching fresh rows, and the apply actions tag the
already-imported uncategorized rows of that counterparty on demand. Deleting a rule (or deleting
its category) stops auto-tagging and **reverts** to uncategorized the rows it had tagged (same
merchant and the rule's category). Deleting a category removes its rules.

Unlink actions: `POST /api/v1/categories/{id}/uncategorize` clears that category from its
transactions (keeping the category); `POST /api/v1/merchant-rules/{id}/unlink` clears the rows a
default tagged (reverting them to uncategorized) while keeping the default itself, so it still
auto-tags future imports; and `POST /api/v1/transactions/categorize` bulk-assigns a
category to listed rows. `POST /api/v1/transactions/uncategorize-all` clears the category of every
categorized non-transfer row (internal transfers keep their reserved tag). Accounts can be removed
wholesale with `DELETE /api/v1/accounts/{id}`, which deletes the account, its statements, and all
its transactions (un-pairing any surviving transfer leg).

Statements: `POST /api/v1/statements` (multipart import; the owning account is read from the
statement — by its account number, else its currency — and created if unknown, so no account is
chosen at upload),
`GET /api/v1/statements` (list, newest first, each with the count of stored transactions it
introduced), `DELETE /api/v1/statements/{id}` (removes the statement and the transaction rows
it introduced, un-pairing any surviving transfer leg, so its file can be re-imported; an account
left with no transactions or statements is removed as well), and
`GET /api/v1/statements/coverage` (per account, the earliest and latest period covered, any hole
between consecutive statements, and any period that has closed without a statement). Original
documents are not stored — only their metadata and the parsed, deduplicated transactions.

Coverage is derived on read by `StatementCoverage` in the domain, from the account's statements
alone — nothing is persisted for it. Periods are compared by **contiguity** (`next.periodStart`
equals `previous.periodEnd` plus one day) rather than by calendar month, because accounts close on
different cycles: some on a fixed day of the month, others at month end. A calendar-month rule
would report false gaps. The next expected period end is the latest one plus whole months, which
preserves both cycle styles (a fixed day is kept, a month end clamps to the shorter month) and
deliberately avoids drifting a month-end cycle backwards. A reported gap or missing period is
*possibly* absent data — a month with no activity may legitimately produce no statement.
