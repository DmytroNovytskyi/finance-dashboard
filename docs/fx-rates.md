# FX Rates (NBP, DB-first)

The base currency is configurable via `FINANCE_BASE_CURRENCY` (default `PLN`). Every stored
`base_amount` is expressed in that currency.

## Port

```java
Optional<BigDecimal> findRate(String currency, LocalDate date);
```

Defined in `domain/port/FxRateProvider`; implemented by
`infrastructure/fx/NbpFxRateProvider`.

## Resolution order

1. Look up `fx_rate` for `(currency, date, base_currency)`.
2. If absent, call the NBP API
   (`GET https://api.nbp.pl/api/exchangerates/rates/a/{currency}/{yyyy-MM-dd}/`, table A mid
   rate) and persist the result.
3. If NBP has no rate for that day (weekend/holiday), use the **last published rate on or
   before** the date and persist it.

A rate is fetched at most once; later imports reuse the stored value. The external service is
only a fallback.

## During import

For each transaction with `currency != base_currency`, resolve the rate for
`transaction_date`, compute `base_amount = amount * rate`, and store `fx_rate` +
`fx_rate_date`. If no rate exists for a currency (not published by NBP), the import still
succeeds but `base_amount` / `fx_rate` stay null; such transactions surface in the UI as
"no rate".

## Rules

- Transfers are excluded from FX conversion and statistics: both legs are `nature=TRANSFER`;
  a conversion between own accounts is reflected by the legs' own amounts, not by an NBP rate.
- Changing the base currency triggers a one-time recompute of stored `base_amount` values from
  the `fx_rate` history, fetching any missing rates via NBP.
- A future provider (ECB, etc.) can be added behind the same `FxRateProvider` port.

## Testing

WireMock stubs the NBP endpoint so FX tests are deterministic and offline. `NbpClient` never
touches the real network in tests.
