# Minion-Rush-Relived

**MRR** — A community-driven server project for Minion Rush.

> **Status: Early Development (MRR v0.3).** MRR does **not** yet support any
> Minion Rush gameplay, protocol or game data of any kind. It is an
> independent backend with its own API; accounts and players are local
> development/synthetic test data only. v0.3 adds a **Java/Spring Boot
> server** and a **native Android reference app** alongside the Python
> server — all speaking the same [MRR API v1](docs/protocol.md).

## Technology Stack

**Python server (v0.2 reference)** — Python 3.11+, FastAPI, Uvicorn,
SQLite (stdlib), Pydantic, pytest.

**Java server (v0.3)** — Java 21, Spring Boot 4.1.1, Gradle, JPA/Hibernate,
SQLite, JUnit 5. See [docs/java-server.md](docs/java-server.md).

**Android app (v0.3)** — Kotlin, Jetpack Compose, Retrofit, coroutines,
kotlinx-serialization. See [docs/android-client.md](docs/android-client.md).

## Development Setup

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -e ".[dev]"
```

## Running the Server

From the `server/python` directory:

```bash
cd server/python
python -m mrr
```

Or, after `pip install -e .`, from anywhere:

```bash
python -m mrr
```

The development server listens on `http://127.0.0.1:8000` (localhost only).

## Running the Java Server

From the `server/java` directory:

```bash
cd server/java
./gradlew bootRun                    # Windows: gradlew.bat bootRun
# → http://127.0.0.1:8080
./gradlew bootRun --args=--server.port=8085   # alternative port
```

The database (`server/java/data/mrr.db`, git-ignored) is created
automatically. Configuration and layout:
[docs/java-server.md](docs/java-server.md).

On **OmniOS CE (SunOS/x86_64)** the sqlite-jdbc jar ships no native library —
build it once with `server/java/tools/omnios/build-sqlitejdbc.sh`, then
`./gradlew test` works as usual: [docs/omnios.md](docs/omnios.md).

## Building the Android App

```bash
cd android
./gradlew assembleDebug              # Windows: gradlew.bat assembleDebug
# → android/app/build/outputs/apk/debug/app-debug.apk
```

The app targets `http://10.0.2.2:8080/` (host machine from the emulator) by
default; override with `-Pmrr.baseUrl=...`. See
[docs/android-client.md](docs/android-client.md).

## Building the MRR Patcher

The **MRR Patcher** is a separate Android app (module `android/patcher/`)
that checks the server for updates, downloads declared patch files into its
own private workspace, verifies them with SHA-256 and applies them there:

```bash
cd android
./gradlew :patcher:assembleDebug
# → android/patcher/build/outputs/apk/debug/patcher-debug.apk (~9.9 MB)
```

Server URL override: `-Pmrr.patcher.baseUrl=...`. The server serves the
manifest at `GET /api/v1/patch/manifest` and patch files at
`GET /api/v1/patch/files/{path}` (directory `server/java/patch-files`,
configurable via `mrr.patch-dir`). Full details:
[docs/patcher.md](docs/patcher.md).

## Configuration

Configuration lives in `server/python/mrr/config.py` with development
defaults. Every value can be overridden with an environment variable:

| Variable | Default | Description |
|---|---|---|
| `MRR_HOST` | `127.0.0.1` | Bind address |
| `MRR_PORT` | `8000` | Bind port |
| `MRR_DATABASE_PATH` | `./data/mrr.db` | SQLite database file |
| `MRR_ENVIRONMENT` | `development` | `development` or `production` |
| `MRR_SESSION_TTL_SECONDS` | `86400` | Session token lifetime |

No secrets are stored in the repository.

## Database

The database file defaults to `./data/mrr.db` (relative to the working
directory) and is **not** created automatically at server start. Initialize it
manually:

```bash
python -m mrr init-db
```

Current tables: `players`, `accounts`, `sessions`, `player_state` — all
reserved for **local/synthetic test data only**. See
[docs/database.md](docs/database.md).

Create a local development account (random password, printed once):

```bash
python -m mrr create-dev-account my-dev-user
```

## Authentication & Sessions

- Passwords are hashed with **PBKDF2-HMAC-SHA256** (600k iterations, random
  per-password salt) — never stored or logged in plaintext.
- Login issues a cryptographically random bearer token; only its **SHA-256
  hash** is stored server-side, together with an expiry timestamp.
- Send the token as `Authorization: Bearer <access_token>`.
- Protected endpoints return `401 Unauthorized` for missing, invalid or
  expired tokens; logout invalidates the session immediately.
- Login failures always return the same error for unknown username and wrong
  password.

Example:

```bash
curl -X POST http://127.0.0.1:8000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"test-password"}'

curl -X POST http://127.0.0.1:8000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"test-password"}'
# → {"access_token":"...","token_type":"bearer","expires_in":86400}

curl http://127.0.0.1:8000/api/v1/profile \
  -H "Authorization: Bearer <access_token>"

curl -X PUT http://127.0.0.1:8000/api/v1/player/state \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"experience":250,"level":3,"coins":1500}'
```

## Running the Tests

From the repository root:

```bash
pytest
```

Tests use temporary databases and never touch your local `data/mrr.db`.

Java server (69 tests):

```bash
cd server/java
./gradlew test
```

Android (41 app unit tests + 68 MRR Patcher unit tests, each run for both
build variants):

```bash
cd android
./gradlew testDebugUnitTest
```

## API

Base path: `/api/v1` (experimental, subject to change). The table below
describes the **Python reference**; the Java server implements the same
contract with ownership-scoped players — differences are listed in
[docs/protocol.md](docs/protocol.md).

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/health` | — | Health check |
| `POST` | `/api/v1/auth/register` | — | Create local account (`201` / `409` / `422`) |
| `POST` | `/api/v1/auth/login` | — | Get bearer token (`200` / `401`) |
| `POST` | `/api/v1/auth/logout` | Bearer | Invalidate session (`204` / `401`) |
| `GET` | `/api/v1/profile` | Bearer | Own account + primary player (`200` / `401`) |
| `GET` | `/api/v1/player/state` | Bearer | Read own save state (`200` / `401`) |
| `PUT` | `/api/v1/player/state` | Bearer | Write own save state (`200` / `401` / `422`) |
| `POST` | `/api/v1/players` | — | Create synthetic test player (`201` / `409` / `422`) |
| `GET` | `/api/v1/players` | — | List test players (`200`, `[]` when empty) |
| `GET` | `/api/v1/players/{player_id}` | Bearer* | Get one player (`200` / `401` / `404`) |
| `DELETE` | `/api/v1/players/{player_id}` | Bearer* | Delete one player (`204` / `401` / `404`) |

\* Players belonging to an account require the owner's token; unowned
synthetic test players stay public for local development.

The Java server additionally serves the public MRR Patcher endpoints
`GET /api/v1/patch/manifest` and `GET /api/v1/patch/files/{path}`
(see [docs/api.md](docs/api.md)); the Python reference implementation does
not.

Example health response:

```json
{
    "status": "ok",
    "service": "mrr-server"
}
```

> These endpoints are an **internal/synthetic test API** for local test data
> only — not a Minion Rush compatible server implementation.

Interactive API docs are served at `/api/docs`. Full details:
[docs/api.md](docs/api.md); cross-server contract:
[docs/protocol.md](docs/protocol.md).

## MRR Protocol

The API documented above is **MRR's own protocol/API**. MRR currently does
**not** implement, and does not claim to implement, the original Minion Rush
backend API, its message formats, encryption, or any Gameloft service
behavior. Compatibility with the original game is a potential future topic
and will be documented separately if/when work on it starts.

## License

This project is licensed under the GNU Affero General Public License v3.0.
See [LICENSE](LICENSE) for the full text.
