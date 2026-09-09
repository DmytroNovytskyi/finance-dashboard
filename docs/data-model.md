# Data Model

Schema is managed by Flyway migrations under
`backend/src/main/resources/db/migration`. This document mirrors the current migration and
must be updated alongside it.

## `account`

An account the user owns (e.g. "Personal PLN", "Business USD"). Accounts are created and
renamed but never deleted.

| column | type | notes |
|---|---|---|
| id | bigserial PK | |
| name | varchar not null | |
| currency | char(3) not null | native currency of the account |
| kind | varchar null | optional `PERSONAL` / `BUSINESS` tag |
| account_number | varchar null | masked; reserved for transfer matching |
| sort_order | int not null default 0 | |
| created_at / updated_at | timestamptz | |

## `category`

A flat list of spending groups. No nesting in v1. All rows are user-editable and deletable;
deleting a category sets its transactions back to uncategorized.

| column | type | notes |
|---|---|---|
| id | bigserial PK | |
| name | varchar not null | |
| color | varchar null | hex, for UI |
| sort_order | int not null default 0 | |
| system | boolean not null default false | reserved categories cannot be deleted |
| created_at / updated_at | timestamptz | |

A small default set (Groceries, Transport, Housing, Dining, Entertainment) is seeded on the
first migration. Migration V3 adds the reserved **Internal Transfer** category (`system = true`,
added as "Transfer", renamed in V4); every `nature = TRANSFER` row is tagged with it (paired legs
and a backfill of existing rows), so internal transfers read and filter as a normal, non-deletable
group.

## `merchant_rule`

A user-managed default that auto-tags imported transactions by counterparty. Matching is exact on
a normalized key (uppercased, whitespace collapsed); a rule applies to future imports and, on the
`apply` action, to already-imported uncategorized rows.

| column | type | notes |
|---|---|---|
| id | bigserial PK | |
| merchant | varchar not null unique | normalized counterparty, e.g. `EXAMPLE MERCHANT` |
| category_id | bigint FK → category not null | on delete cascade — deleting a category drops its rules |
| created_at / updated_at | timestamptz | |

## `bank_statement`

One imported file, attributed to exactly one account.

| column | type | notes |
|---|---|---|
| id | bigserial PK | |
| account_id | bigint FK → account not null | |
| bank | varchar not null | e.g. `MONOBANK` |
| period_start / period_end | date | |
| file_name | varchar | |
| file_hash | varchar unique | SHA-256; dedup on re-import |
| imported_at | timestamptz | |

## `transaction`

| column | type | notes |
|---|---|---|
| id | bigserial PK | |
| statement_id | bigint FK → bank_statement not null | |
| account_id | bigint FK → account not null | denormalized from the statement, for filtering |
| transaction_date | date not null | |
| amount | numeric(19,4) not null | signed: expense negative, income positive |
| currency | char(3) not null | ISO 4217 |
| nature | varchar not null default `EXPENSE` | `INCOME` / `EXPENSE` / `TRANSFER` |
| description | text | raw from the statement |
| merchant | varchar null | extracted counterparty |
| category_id | bigint FK → category, null | null = uncategorized |
| base_amount | numeric(19,4) null | converted to the base currency |
| fx_rate | numeric(20,8) null | rate used |
| fx_rate_date | date null | date of the rate |
| dedup_hash | varchar | (date, amount, currency, description) — idempotent import |
| transfer_group_id | uuid null | shared by the two legs of an internal transfer |
| created_at | timestamptz | |

`EXPENSE` and `INCOME` participate in statistics; `TRANSFER` rows (money moved between the
user's own accounts, including currency conversions) are excluded from all spend/income
stats.

## `fx_rate`

Primary store of FX rates. Historical rates are immutable — once stored, never re-fetched.

| column | type | notes |
|---|---|---|
| id | bigserial PK | |
| currency | char(3) | |
| rate_date | date | |
| rate | numeric(20,8) | mid rate |
| base_currency | char(3) | the configured base currency, e.g. `PLN` |
| source | varchar | e.g. `NBP` |

Unique on `(currency, rate_date, base_currency)`.
