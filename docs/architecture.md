# MRR Architecture

> **Experimental.** The architecture described here is the initial foundation
> and will evolve as the project develops. Nothing in this document is final.

## Current Architecture

```
┌─────────────────────┐
│ Android reference   │   Kotlin, Compose, Retrofit
│ app (v0.3)          │
└──────────┬──────────┘
           ↓  HTTP API  (/api/v1)   — MRR API v1 (protocol.md)
┌──────────┴──────────┐
│ MRR Python server   │   FastAPI reference (v0.2)     MRR Java server
│ :8000               │                                Spring Boot (v0.3) :8080
└──────────┬──────────┘                                └──────────┬──────────┘
           ↓                                                     ↓
┌──────────┴──────────┐                               ┌──────────┴──────────┐
│ SQLite ./data/      │                               │ SQLite data/mrr.db  │
│ mrr.db              │                               │ (JPA-managed)       │
└─────────────────────┘                               └─────────────────────┘
```

Both servers implement the **same API contract** — see
[protocol.md](protocol.md) for the contract and the known differences.
They are independent processes with independent databases; the Android app
defaults to the Java server.

Internal layering inside either server:

```
      API  (HTTP concerns: routes, status codes, JSON)
       ↓
    Service  (business logic, validation)
       ↓
  Repository  (SQL/ORM access)
       ↓
    SQLite
```

See [backend-structure.md](backend-structure.md) for the responsibilities of
each layer.

## Layers

- **Android client** — implemented (v0.3): Kotlin/Compose reference app in
  `android/`. One `MrrViewModel` + state-driven screens; token stored via
  `EncryptedSharedPreferences`; `401` clears the token and returns to login.
  See [android-client.md](android-client.md).
- **Java server** — implemented (v0.3): Spring Boot app in `server/java/`
  implementing MRR API v1 with ownership-scoped players. Same layering as the
  Python server. See [java-server.md](java-server.md).
- **HTTP API** — versioned REST API mounted under `/api/v1`. Endpoint details
  are documented in [api.md](api.md); cross-server contract in
  [protocol.md](protocol.md). It currently exposes health, a
  synthetic test-player API, authentication (`register`/`login`/`logout`),
  a protected profile and protected save-state endpoints. Routes call
  services only and never touch SQLite directly.
- **Service layer** — `mrr/services/`:
  - `players.py` — player validation and CRUD orchestration
  - `accounts.py` — registration (hashed passwords, default player)
  - `authentication.py` — login, sessions, bearer-token authentication
  - `player_state.py` — owner-scoped save state
  No FastAPI dependency.
- **Repository layer** — `mrr/repositories/` (`players`, `accounts`,
  `sessions`, `player_state`) — parameterised SQL only, no HTTP concepts.
- **Security primitives** — `mrr/security.py`: PBKDF2 password hashing,
  random session tokens, SHA-256 token hashing.
- **MRR Python server** — the FastAPI/Uvicorn process defined in
  `server/python/mrr/`. Configuration is read from `mrr/config.py`
  (`MRR_*` environment variables).
- **SQLite database** — created by `python -m mrr init-db`, never
  automatically at request time. Schema and usage are documented in
  [database.md](database.md). Currently limited to synthetic local test data.

## Authentication Flow

```
POST /api/v1/auth/login
    → verify PBKDF2 password hash
    → create random session token
    → store SHA-256(token) + expiry  (never the token itself)

Request with Authorization: Bearer <token>
    → get_current_account() dependency
    → hash token, look up session, check expiry, load account
    → 401 when missing/invalid/expired
```

Protected endpoints: `GET /api/v1/profile`, `GET|PUT /api/v1/player/state`,
`POST /api/v1/auth/logout`. Owned players are additionally protected on
`GET|DELETE /api/v1/players/{player_id}`.

## Code Layout

```
server/python/mrr/
├── __init__.py
├── __main__.py        # CLI: serve | init-db | create-dev-account
├── config.py          # Settings + MRR_* environment overrides
├── security.py        # PBKDF2 hashing, session tokens, token hashing
├── db/
│   ├── __init__.py
│   ├── connection.py  # short-lived SQLite connections (foreign keys on)
│   └── schema.py      # accounts/players/sessions/player_state + migration
├── repositories/
│   ├── __init__.py
│   ├── players.py
│   ├── accounts.py
│   ├── sessions.py
│   └── player_state.py
├── services/
│   ├── __init__.py
│   ├── players.py
│   ├── accounts.py
│   ├── authentication.py
│   └── player_state.py
└── api/
    ├── __init__.py
    ├── main.py        # FastAPI app, mounts the v1 router
    ├── deps.py        # DI: services, get_current_account (overridable)
    └── v1/
        ├── __init__.py     # aggregates the v1 routers
        ├── health.py       # GET /api/v1/health
        ├── players.py      # players CRUD (ownership enforced)
        ├── auth.py         # register / login / logout
        ├── profile.py      # GET /api/v1/profile
        └── player_state.py # GET|PUT /api/v1/player/state
```

```
server/java/src/main/java/de/freeway/mrr/
├── MrrApplication.java
├── config/            # mrr.data-dir EnvironmentPostProcessor
├── model/             # Account, Session, Player, PlayerState (JPA)
├── repository/        # Spring Data + EntityManager-based PlayerStateRepository
├── security/          # PasswordHasher, TokenService, AuthFilter
├── service/           # Account, Authentication, Player, PlayerState
├── api/               # controllers (DTOs nested per controller)
├── exception/         # GlobalExceptionHandler → {"detail": ...}
└── util/              # TimeUtil (ISO-8601 UTC, second precision)

android/app/src/main/java/de/freeway/mrr/android/
├── MrrApp.kt          # Application + AppContainer
├── MainActivity.kt    # screen switching (state-driven, no navigation lib)
├── api/               # Retrofit MrrApi, Models, ApiClient, MrrApiException
├── data/              # TokenStore (EncryptedSharedPrefs), MrrRepository
└── ui/                # MrrViewModel + screens/ + theme/
```

`api/main.py` builds the FastAPI application and includes the versioned
router; route definitions live in `api/v1/`. Future versions (e.g. `v2`) can
be added as sibling packages without touching `main.py` beyond one
`include_router` call.

## API Versioning

- Base path: `/api/v1/`
- Breaking changes are introduced under a new version prefix (`/api/v2/`).
- The API is experimental and subject to change. See [api.md](api.md).

## MRR Protocol

MRR exposes **its own API**. It does not claim compatibility with the
original Minion Rush backend API and implements no Minion Rush protocol,
encryption or proprietary message format.

## Explicit Non-Goals (for now)

The foundation intentionally contains none of the following:

- compatibility with the original Minion Rush backend protocol
- leaderboards, matchmaking or in-app purchases
- game assets or copyrighted game files
- hardcoded secrets, real credentials or real user data
- connections to production game servers

These may be considered in later design iterations.
