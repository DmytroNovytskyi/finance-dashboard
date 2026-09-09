# FX Rates (NBP, DB-first, valued at import)

Every transaction is valued in **every supported currency at its own transaction date**, once,
at import time, and the resulting values are stored in `transaction_amount`. Statistics then sum
the stored value in the requested currency; there is **no read-time conversion** and no concept of
an "unconverted" transaction.

The rate denominator is the configurable base currency (`FINANCE_BASE_CURRENCY`, default `PLN`).
The other supported currencies come from `FINANCE_CURRENCIES` (default `PLN,USD`; the base is
always included). Adding a currency later fills the missing rows via the backfill.

## Port

```java
Optional<FxRate> findRate(String currency, LocalDate date);   // FxRate = (rate, date)
```

Defined in `domain/port/FxRateProvider`; implemented by
`infrastructure/fx/NbpFxRateProvider`. `rate` is **PLN-per-1-foreign-unit** (`R(code)`,
`R(base) = 1`).

## Resolution order

1. Look up `fx_rate` for `(currency, date, base_currency)`.
2. If absent, call the NBP API
   (`GET https://api.nbp.pl/api/exchangerates/rates/a/{currency}/{yyyy-MM-dd}/`, table A mid
   rate) and persist the result.
3. If NBP has no rate for that day (weekend/holiday), use the **last published rate on or
   before** the date and persist it.

A rate is fetched at most once; later imports reuse the stored value. The external service is
only a fallback.

## Valuing a transaction at import

For each fresh row of native amount `A` in currency `N`, the stored value in supported currency
`C` is:

- `A` itself when `C == N` (the native row is exact);
- `A × R(N) / R(C)`, rounded to 4 decimal places (half-up), otherwise.

Because rates are denominated in the base and `R(base) = 1`, a PLN transaction's PLN row is `A`
and its USD row is `A / R(USD)`. The same value is written whether the statement is PLN- or
foreign-denominated.

### Fail-fast rejection

The rate for a `(currency, date)` must be resolvable **on or after `date - lookback`**
(`FINANCE_FX_IMPORT_LOOKBACK_DAYS`, default 5). If a needed rate cannot be resolved within that
window — NBP unreachable, or the transaction predates the oldest published rate — the **whole
statement import fails with HTTP 422** and nothing is saved. There is no partial import and no
silent "unconverted" row. A PLN statement still needs every other supported currency's rate for
its dates, because each transaction is valued in all of them.

## Rules

- Transfers are excluded from statistics (both legs are `nature=TRANSFER`); their own amounts
  are what move between the accounts. A conversion between own accounts is not re-derived from an
  NBP rate.
- Rows are immutable facts. They are never recomputed on read and never updated in place.
- Changing the base currency or the supported set changes which values future imports and the
  backfill write; stored rows are historical and left as-is.

## Backfilling existing rows

`TransactionAmountBackfillService.backfillAll()` fills the `transaction_amount` rows that legacy
transactions (imported before this table) or a newly added currency are missing. It is idempotent:
a row that already exists is left untouched, and a transaction whose rate cannot be resolved is
skipped so the pass can be re-run later. It runs once at startup behind
`FX_BACKFILL_ON_STARTUP` (`finance.fx.backfill-on-startup`, default off) — for example
`FX_BACKFILL_ON_STARTUP=true ./gradlew bootRun`.

## Testing

WireMock stubs the NBP endpoint so FX tests are deterministic and offline. `NbpClient` never
touches the real network in tests. Integration fixtures keep `finance.currencies` to PLN so no
rate is needed unless a test opts in to a foreign statement.
