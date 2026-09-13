# Finance Dashboard — Project Notes

## What this is

A personal expense dashboard: import bank statement files, parse them into a normalized
transaction model, categorize spending, and view statistics. Backend is Java 21 +
Spring Boot 3 (Gradle), frontend is Vite + React + TypeScript (MUI), DB is PostgreSQL 18.
`docs/` is the source of architecture truth — keep it current when the system changes.

## Build / run / test

**Export the DB credentials first** — they are not committed (see "Secrets and data hygiene"):
`export SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=...`

- Backend tests + build: `cd backend && ./gradlew test`, `./gradlew build`
- Backend coverage report: `./gradlew jacocoTestReport` (threshold enforced)
- Backend run: `./gradlew bootRun` (http://localhost:8080)
- Frontend dev: `cd frontend && npm install && npm run dev`
- DB (local dev): external Compose file wiring the `data/` directory
- Deploy: see the README (clone → copy `deploy/` → fill `.env` → `docker compose up -d`)

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
  `nature=TRANSFER` and are excluded from all statistics. Refund legs are `nature=REFUND`; they are
  excluded as rows, but each group's net is folded back in once, on its last leg, under the reserved
  Refund category.

## Testing requirement

Java code is covered by tests: unit (JUnit 5 + AssertJ) for domain/use cases/parsers/FX,
integration (`@SpringBootTest` + MockMvc) for persistence and the REST API, WireMock for the
external NBP HTTP. JaCoCo enforces a line-coverage threshold.

**The integration tests run against a plain local Postgres** — the `finance_test` database on
`localhost:5432`, with the same credentials as dev. There is no Testcontainers here, so the
database has to be running before `./gradlew test`. There are **no frontend tests**: verify
frontend changes with `tsc`, the build, and inspection.

## Secrets and data hygiene

This repository is **public**. Nothing that identifies a real account, counterparty or person
belongs in it, in any file — code, test, fixture, doc, comment, or commit message.

- **Credentials come from the environment.** `application.yml` and `application-test.yml` read
  `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` with **no committed fallback**, so
  both `bootRun` and `./gradlew test` need them exported. `.env` lives on the deploy host,
  outside the repo. Never write a literal one into a YAML, a doc, or a test.
- **Fixtures use synthetic counterparties** — `EXAMPLE MERCHANT`, `EXAMPLE STORE`, `Example
  Shop`, `Example Payee`. `PekaoTestPdf` is the model to copy: it builds statements from
  obviously fake rows and says so in its Javadoc.
- **Bank wording is a contract, not data.** Strings the parsers and the refund matcher key on
  (`ANULOWANIE TRANSAKCJI`, `WYKONANIEJ:`, `ZWROT PŁATNOŚCI`, `DN.`) stay verbatim — they are
  what the code matches, and scrubbing them breaks parsing.
- **Never paste a real statement.** The real PDFs live in `local/`, which is gitignored, and so
  does the optional real-statement test that reads them. Samples in parser Javadoc are rewritten
  to keep the layout but lose the counterparty.
- **Scan the diff before pushing.** `git diff --cached` is the last cheap moment, and a
  counterparty you recognise is the thing to look for — not just the word "password".

### If something does slip in

Deleting it in a new commit is not enough: it stays in every earlier commit, and this repo is
public. The procedure used on 2026-09-13:

1. `git bundle create local/pre-scrub.bundle --all` — recoverable until you are sure.
2. Write an **idempotent** `sed` script under `local/` (never committed). Test it against a
   scratch copy first: shell quoting mangles backslashes, and a pattern that silently matches
   nothing looks exactly like success.
3. Apply it to the working tree, verify, commit.
4. `git filter-branch --tree-filter 'sh /abs/path/local/<script>' -- --all`.
5. Verify with `git log --all -S'<the string>'` — it must print nothing — then
   `git reflog expire --expire=now --all && git gc --prune=now --aggressive`.
6. `git push --force-with-lease`. **Every SHA changes**, so other clones need
   `git fetch && git reset --hard origin/main`, never a pull. The server's clone is one of them.

## Layout reference

- `docs/` — architecture, data model, parser runbook, FX rate rules
- `deploy/` — committed Compose template, no secrets
- `local/` — dev workspace (ignored); holds the real statements, the handoff, and any scrub script
