# Finance Dashboard — frontend

React + TypeScript single-page app (Vite, MUI, TanStack Query, Recharts) for the finance
dashboard. Talks to the backend REST API under `/api/v1`.

## Local development

```bash
npm install
npm run dev        # http://localhost:5173, proxies /api to http://localhost:8080
```

## Production

Built to static assets and served by nginx (`Dockerfile`), which also proxies `/api` to the
backend. See `docs/deployment.md` in the repository root.

## Layout

- `src/theme.ts` — light/dark MUI theme and the shared chart palette.
- `src/types/` — TS types mirroring the backend DTOs.
- `src/api/` — fetch client, endpoint functions, query keys.
- `src/features/<name>/` — one folder per page (overview, transactions, categorize, import).
- `src/app/AppShell.tsx` — navigation shell.
