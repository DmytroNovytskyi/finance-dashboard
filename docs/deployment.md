# Deployment

The Compose file, secrets, and the database data directory live **outside** the repository.
The repo is cloned into a deploy directory. `deploy/docker-compose.yml` in the repo is a
committed template; the live copy is a sibling of `data/`.

## Deploy directory layout

```
<module>/                        # e.g. ~/server/finance-dashboard  (NOT a git repo)
├── docker-compose.yml           # copied from the repo's deploy/ template
├── .env                         # secrets (DB_USER, DB_PASSWORD, …)
├── data/                        # Postgres data (external to the repo)
└── app/                         # git clone of the repo
    ├── backend/
    ├── frontend/
    └── …
```

Build contexts in the template are relative to the Compose file, so `./app/backend` = the
cloned repo and `./data` = the external data directory.

## Steps

```bash
mkdir <module> && cd <module>
git clone <repo-url> app
cp app/deploy/docker-compose.yml ./docker-compose.yml
cp app/.env.example .env      # then fill in DB_USER / DB_PASSWORD
mkdir data
docker compose up -d
```

## Services

- `db` — `postgres:18`, healthchecked, data at `./data` (bind mount).
- `backend` — built from `./app/backend`, no published port (internal only), waits for a
  healthy db.
- `frontend` — built from `./app/frontend`, published on `:8100` by default
  (`FRONTEND_PORT`), serves static assets and proxies `/api` to the backend.

Environment is supplied from `.env` (`DB_USER`, `DB_PASSWORD`, optional `FINANCE_BASE_CURRENCY`,
`TZ`, `FRONTEND_PORT`). No secret appears in the repo.
