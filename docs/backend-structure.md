# MRR Backend Structure

> **Experimental.** The backend layout is in early development and subject to
> change.
>
> This document describes the **Python** server. The Java server mirrors the
> same layering — see [java-server.md](java-server.md).

## Layering

```
API  →  Service  →  Repository  →  SQLite
              ↘  mrr/security.py (hashing, tokens)
```

Each layer only talks to the layer directly below it.

## Responsibilities

### API — `mrr/api/`

- FastAPI application, routing, HTTP status codes, Pydantic request/response
  schemas (HTTP boundary only).
- Mounts the versioned router under `/api/v1`.
- `mrr/api/deps.py` provides dependency factories:
  `get_database_path()`, `get_player_service()`, `get_account_service()`,
  `get_authentication_service()`, `get_player_state_service()`, plus the
  authentication dependencies `get_current_account()` (required, 401 on
  failure) and `get_optional_account()` (None when no token is sent).
  Tests override `get_database_path` to point at a temporary database.
- Routes call **services only** — no SQL, no SQLite connections, no
  repository calls.
- Current routes: `GET /api/v1/health`, player CRUD, `register`/`login`/
  `logout`, `GET /api/v1/profile`, `GET|PUT /api/v1/player/state`.

### Service — `mrr/services/`

| Module | Responsibility |
|---|---|
| `players.py` | Player validation (`level >= 1`, `coins >= 0`, non-empty ids) and CRUD orchestration. |
| `accounts.py` | Registration: validation, password hashing, duplicate handling, default player for a new account. |
| `authentication.py` | Login, session issuance, bearer-token authentication (expiry check), logout. |
| `player_state.py` | Owner-scoped save state: resolve the caller's player, get-or-create / update state. |

All services are plain Python with **no FastAPI dependency** and translate
persistence errors into domain errors
(`PlayerValidationError`, `AccountAlreadyExistsError`, `AuthenticationError`,
`PlayerStateNotFoundError`).

### Repository — `mrr/repositories/`

| Module | Table |
|---|---|
| `players.py` | `players` |
| `accounts.py` | `accounts` |
| `sessions.py` | `sessions` |
| `player_state.py` | `player_state` |

- Pure data access; all queries use **bound parameters** — no SQL string
  concatenation from user input.
- Returns internal dataclasses (`Player`, `Account`, `Session`,
  `PlayerState`); **no HTTP concepts**.
- Each call opens a short-lived connection via `open_connection()`
  (commit on success, rollback on error) and closes it again.

### Security — `mrr/security.py`

- `hash_password` / `verify_password`: PBKDF2-HMAC-SHA256, 600k iterations,
  random salt, constant-time comparison.
- `generate_session_token`: `secrets`-based random bearer token.
- `hash_token`: SHA-256 — only the hash reaches the database.

### Database — `mrr/db/`

- Connection handling, schema initialization and additive migration
  (`python -m mrr init-db`).
- Documented in [database.md](database.md).

## Data Flow Example

```
POST /api/v1/auth/login
    → api/v1/auth.py        (Pydantic body, status codes)
    → AuthenticationService.login(...)
        → AccountRepository.get_account_by_username(...)   (bound params)
        → security.verify_password(hash, password)
        → security.generate_session_token()
        → SessionRepository.create_session(token_hash=sha256(token), ...)
    → { access_token, token_type, expires_in }
```

## Current Non-Goals

No Minion Rush protocol compatibility, no leaderboards/matchmaking/IAP, no
proxying or reverse engineering, no real user data. Only local/synthetic test
data is stored in SQLite; the API is MRR's own API, not a compatible Minion
Rush server implementation.
