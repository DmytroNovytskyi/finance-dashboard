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

`application` exposes orchestration classes such as `StatementImportService`, `TransactionService`,
`TransactionEditService` (categorize, pair/unpair, delete), `CategoryService`, `AccountService`,
`MerchantRuleService`, `TransferSuggestionService`, `RefundSuggestionService`, and
`StatisticsService`. Controllers stay thin and delegate to these.

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
split. `PATCH` carries **only** those two user-owned fields: the currency and the canonical
account number are set by the statements, used to recognize later imports of the same account,
and cannot be changed through the API — `AccountUpdateRequest` omits them, so a client that sends
them has them ignored rather than applied. Accounts have no manual create or edit path in the UI;
the only user actions are rename, tag, and delete.
`nature=TRANSFER` rows (internal transfers between the user's own accounts) are excluded from every
statistic. `nature=REFUND` rows are excluded from income and expense as individual rows, but their
group is not dropped: the net of each group is folded back in once, on the group's last leg, under
the reserved Refund category — see the refund paragraph below. Both natures carry a reserved,
non-deletable category (**Internal Transfer**, **Refund**) so they appear and filter as normal
groups in the UI. Reserved categories are resolved by `category.system_key`, not by name: more than
one exists and the user may rename any of them.

Internal-transfer detection and review: the matcher pairs non-transfer legs between own accounts
that reference each other's account number (mirror, works across currencies/FX) or that match in
amount/currency within 7 days. A pair whose **both legs carry a category** is treated as decided and
is not suggested; if any leg is uncategorized it stays a suggestion and both its legs are tagged
"Internal" in the UI. Import auto-applies a mirror pair whose legs are **both uncategorized**
(each leg becomes `TRANSFER` + Internal Transfer); a categorized leg is never auto-overridden.
Suggestions: `GET /api/v1/transfers/suggestions`, apply one pair `POST /api/v1/transfers`
(`{fromTransactionId,toTransactionId}`), apply all `POST /api/v1/transfers/suggestions/apply`,
and revert an applied transfer `POST /api/v1/transfers/{transactionId}/unlink` (both legs return
to their natural income/expense, with the category cleared). Unlinking — of a transfer or of a
refund — does **not** restore a category a leg carried before it was linked: linking replaced that
category with the reserved one, so unlink can only clear it. Categorize a pair after deciding it,
not before.

Refund detection and review. **Detection** is a matcher and stays strict: a refund candidate is an
incoming row whose wording reverses a payment (`ANULOWANIE TRANSAKCJI`, `ZWROT ... TRANSAKCJI`),
which is what keeps the tax office's `Zwrot z podatku VAT` out — it names no payment, so it is
never a candidate. Pekao embeds the reversed transaction in that wording, and the matcher reads it:
the date as `DN. dd/MM/yyyy` and the merchant after `WYKONANEJ:`. Credits that name the same
reversed transaction — same account, same date, same merchant — are matched as **one group**,
because a single order may come back in parts. The matcher then looks for an expense of that
magnitude in the same account, dated on or before the earliest credit and within 120 days,
preferring a purchase the anchors agree on and otherwise the nearest one; equal amount alone would
mostly surface the user's own settlements. Each purchase is spent on one group. Nothing is linked
automatically, and a suggestion is reported as `ANCHORED` or `AMOUNT` so the reason is visible. A
group stops being suggested once **every** leg of it carries a category, the same rule a transfer
follows.

**Linking** imposes no arithmetic at all. A refund is any group of two or more rows: they need not
add up to each other, share an account or a currency, or have opposite signs. The only rules are
structural — two rows at minimum, no row named twice, and no leg already paired or already wearing
a reserved category. The old sum, sign, account and currency rules existed to stop a group leaving
the statistics with money unaccounted for, and they were removed only once the statistics stopped
dropping a group: each one is folded back in as its **net**, dated at its **last leg** and filed
under the reserved Refund category, so income − expense still reconciles with what actually moved.
A group whose net is exactly zero contributes nothing, which is why a balanced reversal reads
exactly as it always did. Folding counts only the legs inside the requested range, so a group
straddling a period boundary still totals correctly across periods. This means the figures inside a
group's span are **settled, not accrued**: a January credit belonging to a group that closes in
February is reported in February.

Suggestions: `GET /api/v1/refunds/suggestions`, link a group `POST /api/v1/refunds`
(`{transactionIds:[...]}`), link all `POST /api/v1/refunds/suggestions/apply`, and revert
`POST /api/v1/refunds/{transactionId}/unlink` (every leg returns to its natural income/expense and
category).

Transactions: `GET /api/v1/transactions` lists filtered rows (`accountId`, `categoryId`/`uncategorized`,
`nature`, `from`/`to`, `q`, `ids` repeated, `merchant`, `withoutMerchant`, `page`/`size`) ordered by `sort`
(`date|amount|account|category`) with `order` (`asc|desc`; default date descending). Account and
category order by their names. `amount` orders by the row's **worth in the base currency**, not by
the native amount it reports, so a list holding more than one currency reads on a single scale —
which is why the `amount` column is free not to run monotonically. A row with no stored value for
the base currency keeps its place in the list (the statistics skip such rows; the list must show
them) and orders last in both directions. `ids` restricts the list to an explicit set of rows — an
empty or absent list means no constraint — which is how clicking a suggested pair shows the
transactions it refers to.

Statistics: `GET /api/v1/statistics/summary?from&to[&accountId|kind][&topN][&granularity][&displayCurrency]`
reports period totals (income, expense magnitude, net = income − expense, transaction count,
uncategorized count, average daily expense) plus the same split by month, by category
(uncategorized bucketed as `(uncategorized)`), top merchants, and a `trend` of time buckets.
`topN` bounds the merchant list **per direction**: it carries the `topN` merchants by spend and the
`topN` by income together, because a list ranked by spend alone can never hold a merchant that only
ever received money — every one of them ties at zero spend, so any cut by rank drops them all. The
list leads with the spend ranking and appends the income-only merchants after it. The
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

Merchant defaults: `GET/POST /api/v1/merchant-rules`, `DELETE /api/v1/merchant-rules/{id}` (drops the
rule and reverts its rows, reporting the count the way unlink does),
`POST /api/v1/merchant-rules/{id}/unlink` (keeps the rule, reverts its rows),
`POST /api/v1/merchant-rules/{id}/apply` (one rule), and `POST /api/v1/merchant-rules/apply`
(all rules). A rule maps a counterparty to a category, and `matchType` says how the two are
compared: `EQUALS` (the default, and the only behaviour before match types existed), `STARTS_WITH`
or `CONTAINS`. Both sides are compared normalized — trimmed, uppercased, whitespace collapsed — so
case and spacing never matter whichever type is used. A rule created without a `matchType` is
`EQUALS`, which is what keeps rules stored before the feature behaving exactly as they did.

Once matching stopped being exact, one counterparty could be claimed by several rules, so
`MerchantRuleMatcher` resolves them: the narrower match type wins (`EQUALS` over `STARTS_WITH` over
`CONTAINS`), then the longer text, then the lower id — an order independent of how the rules are
stored, so a narrow rule can refine a broad one without deleting it.

Statement imports auto-tag matching fresh rows, and the apply actions tag the already-imported
uncategorized rows of that counterparty on demand. **Every path that resolves a counterparty goes
through that one matcher** — the import, both apply actions, and the revert. They have to agree: a
rule that tags rows on import but does not recognise them when deleted would leave those rows
carrying a category with nothing to point at. Deleting a rule (or deleting its category) stops
auto-tagging and **reverts** to uncategorized the rows it had tagged — those the matcher claims
whose category is the rule's. Deleting a category removes its rules.

Unlink actions: `POST /api/v1/categories/{id}/uncategorize` clears that category from its
transactions (keeping the category); `POST /api/v1/merchant-rules/{id}/unlink` clears the rows a
default tagged (reverting them to uncategorized) while keeping the default itself, so it still
auto-tags future imports; and `POST /api/v1/transactions/categorize` bulk-assigns a
category to listed rows. `POST /api/v1/transactions/uncategorize-all` clears the category of every
categorized row that is not excluded by nature (internal transfers and refunds keep their reserved
tag). Accounts can be removed wholesale with `DELETE /api/v1/accounts/{id}`, which deletes the
account, its statements, and all its transactions (un-pairing any surviving transfer or refund leg).

Statements: `POST /api/v1/statements` (multipart import; the owning account is read from the
statement — by its account number, else its currency — and created if unknown, so no account is
chosen at upload),
`GET /api/v1/statements?[sort][&order][&accountId]` (list, each with the count of stored
transactions it introduced; ordered by `sort` = `imported|file|account|period` with `order` =
`asc|desc`, defaulting to newest import, and optionally restricted to one account — an
unrecognised `sort` falls back to that default), `DELETE /api/v1/statements/{id}` (removes the statement and the transaction rows
it introduced, un-pairing any surviving transfer or refund leg, so its file can be re-imported; an account
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
