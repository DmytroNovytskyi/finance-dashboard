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
│   └── 18/docker/               # the cluster itself (see "Database data" below)
└── app/                         # git clone of the repo
    ├── backend/
    ├── frontend/
    └── …
```

Build contexts in the template are relative to the Compose file, so `./app/backend` = the
cloned repo and `./data` = the external data directory.

The live `docker-compose.yml` is a **copy**, not a symlink — editing the template in the repo
does not change the running stack. After a `git pull` that touches `deploy/`, re-copy it:

```bash
diff app/deploy/docker-compose.yml ./docker-compose.yml   # check for drift
cp app/deploy/docker-compose.yml ./docker-compose.yml
```

## Steps

```bash
mkdir -p <module> && cd <module>
git clone <repo-url> app
cp app/deploy/docker-compose.yml ./docker-compose.yml
cp app/.env.example .env      # then fill in DB_USER / DB_PASSWORD
mkdir data
docker compose up -d --build
```

`data/` must be **empty** on the first `up`. The Postgres entrypoint refuses to initialise if it
finds an existing cluster, and that guard is what stops it silently adopting a foreign data
directory.

The first build pulls the Postgres, JDK, Node, and nginx images and compiles both applications,
so expect roughly 5–10 minutes on modest hardware. Later rebuilds reuse the Gradle and npm
caches and take 2–3 minutes.

## Services

| Service | Container | Image | Published | Notes |
|---|---|---|---|---|
| `db` | `finance-db` | `postgres:18` | none | Data at `./data`, healthchecked |
| `backend` | `finance-backend` | built from `./app/backend` | none | Waits for a healthy db, runs Flyway on start |
| `frontend` | `finance-frontend` | built from `./app/frontend` | `${FRONTEND_PORT:-8100}` | nginx serving the SPA and proxying `/api` |

All three use `restart: unless-stopped`. Docker does not honour `depends_on` ordering on host
boot, so without a restart policy the backend can race Postgres, fail its Flyway migration, and
stay down until someone intervenes.

Neither `db` nor `backend` publishes a port: the database is reachable only from the Compose
network, and the API only through the frontend's `/api` proxy. The UI is therefore the single
entry point, on `http://<host>:8100` by default.

## Database data

Postgres 18 sets `PGDATA=/var/lib/postgresql/18/docker` and declares
`VOLUME /var/lib/postgresql`. The service mounts the **parent** directory:

```yaml
volumes:
  - ./data:/var/lib/postgresql
```

Mounting the older `/var/lib/postgresql/data` path — correct for Postgres 17 and earlier —
writes nothing to the host. The cluster then lives in an anonymous volume that survives a
`restart` but is destroyed by `docker compose down`, taking every transaction with it. For the
same reason the template does not override `PGDATA`.

The cluster ends up nested at **`./data/18/docker`**, so back up `./data` as a whole rather than
`./data/*`. The image's entrypoint chowns the tree to the `postgres` user itself; no host-side
`chown` is needed.

## Configuration

Environment is supplied from `.env` (outside the repo). `DB_USER` and `DB_PASSWORD` are
required — Compose fails with a named error rather than starting with empty credentials.

| Variable | Default | Purpose |
|---|---|---|
| `DB_USER` / `DB_PASSWORD` | — | Postgres credentials; also used by the backend |
| `FINANCE_BASE_CURRENCY` | `PLN` | Currency amounts are stored in |
| `TZ` | `Europe/Warsaw` | Container timezone |
| `FRONTEND_PORT` | `8100` | Host port for the UI |

No secret appears in the repo, `deploy/`, code, `application.yml`, README, or docs.

## Updating a running deployment

```bash
cd <module>/app && git pull
cd .. && docker compose up -d --build
```

Compose rebuilds only the images whose sources changed. The database and its volume are
untouched, and Flyway applies any new migrations on backend start.
