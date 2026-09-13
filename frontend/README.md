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
backend. See the Deploy section of the repository README.

## Layout

- `src/theme.ts` — light/dark MUI theme and the shared chart palette.
- `src/types/` — TS types mirroring the backend DTOs.
- `src/api/` — fetch client, endpoint functions, query keys.
- `src/components/` — shared UI: page layout, chart card, date range control, currency select.
- `src/hooks/` — reusable behaviour: fitting rows to the page, wheel paging.
- `src/lib/` — pure helpers: dates, formatting.
- `src/features/<name>/` — one folder per page: `overview`, `transactions`, `trends`,
  `categorize`, `import`, `accounts`, `preferences`.
- `src/app/AppShell.tsx` — navigation shell.
