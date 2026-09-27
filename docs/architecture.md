# MRR Architecture

> **Experimental.** The architecture described here is the initial foundation
> and will evolve as the project develops. Nothing in this document is final.

## Current Architecture

```
Android/client
      ↓
   HTTP API  (/api/v1)
      ↓
MRR Python server
      ↓
SQLite database  (./data/mrr.db)
```

Internal layering inside the server:

```
      API  (FastAPI, HTTP concerns)
       ↓
    Service  (business logic, validation)
       ↓
  Repository  (SQL access, dataclasses)
       ↓
    SQLite
```

See [backend-structure.md](backend-structure.md) for the responsibilities of
each layer.

## Layers

- **Android/client** — planned client of the HTTP API. Not implemented.
- **HTTP API** — versioned REST API mounted under `/api/v1`. Endpoint details
  are documented in [api.md](api.md). It currently exposes the health endpoint
  and a synthetic test-player API (`/api/v1/players`); routes call
  `PlayerService` only and never touch SQLite directly.
- **Service layer** — `mrr/services/players.py`, business rules and
  validation. No FastAPI dependency.
- **Repository layer** — `mrr/repositories/players.py`, parameterised SQL
  only. No HTTP concepts.
- **MRR Python server** — the FastAPI/Uvicorn process defined in
  `server/python/mrr/`. Configuration is read from `mrr/config.py`
  (`MRR_*` environment variables).
- **SQLite database** — created by `python -m mrr init-db`, never
  automatically at request time. Schema and usage are documented in
  [database.md](database.md). Currently limited to synthetic local test data.

## Code Layout

```
server/python/mrr/
├── __init__.py
├── __main__.py        # CLI: `python -m mrr [serve|init-db]`
├── config.py          # Settings + MRR_* environment overrides
├── db/
│   ├── __init__.py
│   ├── connection.py  # short-lived SQLite connections (foreign keys on)
│   └── schema.py      # CREATE TABLE IF NOT EXISTS players
├── repositories/
│   ├── __init__.py
│   └── players.py     # Player dataclass + PlayerRepository (SQL only)
├── services/
│   ├── __init__.py
│   └── players.py     # PlayerService (validation + orchestration)
└── api/
    ├── __init__.py
    ├── main.py        # FastAPI app, mounts the v1 router
    ├── deps.py        # get_player_service() dependency (overridable in tests)
    └── v1/
        ├── __init__.py   # aggregates the v1 routers
        ├── health.py     # GET /api/v1/health
        └── players.py    # players CRUD routes (calls PlayerService only)
```

`api/main.py` builds the FastAPI application and includes the versioned
router; route definitions live in `api/v1/`. Future versions (e.g. `v2`) can
be added as sibling packages without touching `main.py` beyond one
`include_router` call.

## API Versioning

- Base path: `/api/v1/`
- Breaking changes are introduced under a new version prefix (`/api/v2/`).
- The API is experimental and subject to change. See [api.md](api.md).

## Explicit Non-Goals (for now)

The foundation intentionally contains none of the following:

- authentication or accounts
- game saves, leaderboards, or player data
- Minion Rush protocol behavior
- game assets or copyrighted game files
- credentials, tokens, or secrets
- connections to production game servers

These may be considered in later design iterations.
