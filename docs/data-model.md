# Data Model

Schema is managed by Flyway migrations under
`backend/src/main/resources/db/migration`. This document mirrors the current migration and
must be updated alongside it.

## `account`

An account the user owns (e.g. "Pekao PLN", "Business USD"). Accounts are **created
automatically** by a statement import that names them (currency and account number come from
the file, the name is a default label) and are **removed automatically** once they hold no
transactions or statements (deleting an account's last statement deletes the account). They can
also be **deleted explicitly**, which removes the account, its statements, and all its
transactions (re-importing a statement recreates the account). The user renames the account and
tags its `kind`; those two are the only fields a client can change (`PATCH` ignores anything
else). `currency` and `account_number` are **server-owned**: both are written from the imported
statement, and `account_number` is stored in canonical digits-only form so later statements of
the same account are matched to it.

| column | type | notes |
|---|---|---|
| id | bigserial PK | |
| name | varchar not null | defaulted at import, user-editable |
| currency | char(3) not null | native currency of the account |
| kind | varchar null | optional `PERSONAL` / `BUSINESS` tag |
| account_number | varchar null | canonical digits-only; matches statements on import |
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
| system_key | varchar(32) null | `TRANSFER` / `REFUND`; null for user categories |
| created_at / updated_at | timestamptz | |

A small default set (Groceries, Transport, Housing, Dining, Entertainment) is seeded on the
first migration. Migration V3 adds the reserved **Internal Transfer** category (`system = true`,
added as "Transfer", renamed in V4); every `nature = TRANSFER` row is tagged with it (paired legs
and a backfill of existing rows), so internal transfers read and filter as a normal, non-deletable
group. Migration V6 adds the reserved **Refund** category the same way, and `system_key` with it:
reserved categories are looked up by key, never by name, because more than one of them exists and
the user may rename any of them.

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
| period_start / period_end | date | nullable; drive statement coverage, derived on read |
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
| nature | varchar not null default `EXPENSE` | `INCOME` / `EXPENSE` / `TRANSFER` / `REFUND` |
| description | text | raw from the statement |
| merchant | varchar null | extracted counterparty |
| category_id | bigint FK → category, null | null = uncategorized |
| dedup_hash | varchar | (date, amount, currency, description) — idempotent import |
| transfer_group_id | uuid null | shared by the two legs of an internal transfer |
| refund_group_id | uuid null | shared by a purchase and every credit that reverses it (an order can come back in parts) |
| created_at | timestamptz | |

`EXPENSE` and `INCOME` participate in statistics; `TRANSFER` rows (money moved between the
user's own accounts, including currency conversions) and `REFUND` rows (a purchase and the money
the bank gave back for it) are excluded from all spend/income stats. The `amount`/`currency` pair
is the **native** value from the statement (what the account is denominated in) — see
`transaction_amount` for the per-currency views.

## `transaction_amount`

Each transaction is valued in every supported currency at its own transaction date. The row for
the native currency carries the amount verbatim; every other row is derived at import time from
the `fx_rate` table (or live NBP within a short lookback window, see `docs/fx-rates.md`). Rows are
immutable facts: written once at import, cascade-deleted with their parent, never updated in
place.

| column | type | notes |
|---|---|---|
| transaction_id | bigint FK → transaction not null | on delete cascade |
| currency | char(3) not null | ISO 4217 |
| amount | numeric(19,4) not null | the transaction valued in `currency` |

Primary key is `(transaction_id, currency)`. Statistics **sum the stored amount in the requested
currency**; there is no read-time conversion and no "unconverted" concept. Existing transactions
imported before this table (or missing a newly-added currency) are filled by the idempotent
backfill, never recomputed on read.

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
