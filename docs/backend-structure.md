# MRR Backend Structure

> **Experimental.** The backend layout is in early development and subject to
> change.

## Layering

```
API  →  Service  →  Repository  →  SQLite
```

Each layer only talks to the layer directly below it.

## Responsibilities

### API — `mrr/api/`

- FastAPI application, routing, HTTP status codes, Pydantic request/response
  schemas (HTTP boundary only).
- Mounts the versioned router under `/api/v1`.
- `mrr/api/deps.py` provides `get_player_service()`, a FastAPI dependency that
  builds a `PlayerService` from the configured database path. Tests override
  it to point at a temporary database.
- Routes in `mrr/api/v1/players.py` call **only** the `PlayerService` — no
  SQL, no SQLite connections, no repository calls.
- Current routes: `GET /api/v1/health`, `POST/GET/DELETE /api/v1/players`,
  `GET /api/v1/players/{player_id}`.

### Service — `mrr/services/players.py`

- Business logic and validation (`player_id`/`display_name` non-empty,
  `level >= 1`, `coins >= 0`).
- Translates persistence errors (`sqlite3.IntegrityError`) into domain
  errors (`PlayerValidationError`, `PlayerAlreadyExistsError`).
- **No FastAPI dependency** — plain Python, usable from scripts and tests.
- Constructs a `PlayerRepository` for the configured database path.

### Repository — `mrr/repositories/players.py`

- Pure data access for the `players` table: `create_player`,
  `get_player_by_id`, `get_player_by_player_id`, `delete_player`,
  `list_players`.
- All queries use **bound parameters**; no SQL string concatenation from
  user input.
- Returns the internal `Player` dataclass (id, player_id, display_name,
  level, coins, created_at) or `None`.
- **No HTTP concepts** (no status codes, no API objects).
- Each call opens a short-lived connection via `open_connection()`
  (commit on success, rollback on error) and closes it again.

### Database — `mrr/db/`

- Connection handling, schema initialization (`python -m mrr init-db`).
- Documented in [database.md](database.md).

## Data Flow Example

```
service.create_player(player_id="x", display_name="y")
    → validates input
    → repository.create_player(...)
        → open_connection()  (INSERT + SELECT, commit, close)
    → returns Player dataclass
```

## Current Non-Goals

No authentication, no accounts, no tokens, no real Minion Rush data, no
proxying or reverse engineering. Only local/synthetic test data is stored in
SQLite, and the player API is an internal test API — not a compatible
Minion Rush server implementation.
