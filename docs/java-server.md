# MRR Java Server

> **Experimental.** The Java server is the MRR **v0.3** reference
> implementation of [protocol.md](protocol.md) / [api.md](api.md), built for
> the Android client. It runs alongside the Python server — neither replaces
> the other.

## Technology

| Concern | Choice |
|---|---|
| Language | Java 21 (toolchain) |
| Framework | Spring Boot 4.1.1 (`starter-web`, `starter-validation`, `starter-data-jpa`) |
| Build | Gradle (wrapper, Gradle 9.2.1) |
| Database | SQLite via `hibernate-community-dialects` + `sqlite-jdbc` |
| Migrations | Hibernate `ddl-auto=update` (development; see [database.md](database.md)) |
| Tests | JUnit 5 + `MockMvc` (`spring-boot-webmvc-test`) |
| Passwords | PBKDF2-HMAC-SHA256, same `pbkdf2_sha256$...` encoding as Python |
| Sessions | Opaque bearer token, stored only as SHA-256 hash |

## Layout

```
server/java/
├── build.gradle / settings.gradle / gradle.properties
├── gradlew, gradlew.bat
└── src/
    ├── main/java/de/freeway/mrr/
    │   ├── MrrApplication.java
    │   ├── config/      # DataDirectoryEnvironmentPostProcessor (mrr.data-dir)
    │   ├── model/       # Account, Session, Player, PlayerState (JPA)
    │   ├── repository/  # Spring Data repos + EntityManager-based PlayerStateRepository
    │   ├── security/    # PasswordHasher, TokenService, AuthFilter, Hex
    │   ├── service/     # Account, Authentication, Player, PlayerState services
    │   ├── api/         # health/auth/profile/player/player_state controllers
    │   ├── exception/   # GlobalExceptionHandler -> {"detail": ...} (422/404/405/415 parity)
    │   └── util/        # TimeUtil (ISO-8601 UTC, second precision)
    ├── main/resources/  # application.yml, META-INF/spring.factories
    └── test/            # 45 tests: API contracts + hashing/tokens
```

Layering matches the Python server: **API → Service → Repository → SQLite**.
The `AuthFilter` runs before the API layer, resolves `Authorization: Bearer`
tokens to an account, and writes the same `401 {"detail": ...}` payloads the
Python server uses.

## Running

```bash
cd server/java
./gradlew bootRun            # Windows: gradlew.bat bootRun
# → http://127.0.0.1:8080
```

Run on another port (e.g. next to the Python server):

```bash
./gradlew bootRun --args=--server.port=8085
```

Configuration (defaults, all overridable with `--args=--key=value`):

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `mrr.data-dir` | working dir | Directory for `mrr.db` (env `MRR_DATA_DIR`) |
| `spring.datasource.url` | `jdbc:sqlite:${mrr.data-dir}/mrr.db` | SQLite file |
| `mrr.session-ttl-seconds` | `86400` | Session lifetime |
| `mrr.password-iterations` | `600000` | PBKDF2 iterations (tests use 10000) |

The database file lives in `server/java/data/mrr.db` (git-ignored) and is
created automatically on first start — unlike the Python server's explicit
`init-db` step.

> If port 8080 is taken on your machine, pass `--server.port=...` (the
> Python dev server uses 8000 by default, so 8080/8085 usually coexist fine).

## Testing

```bash
cd server/java
./gradlew test
```

45 tests cover: health, auth (register/login/logout/expiry/ownership),
profile composition from `player_state`, state CRUD/validation, player
ownership (`401`/`404`), password hashing round-trips, token hashing, and the
`{"detail"}` error shapes for `404`/`405`/`415`/`422`.

Tests run against an in-memory SQLite database with reduced PBKDF2 iterations
(`src/test/resources/application.yml`) — no file DB is touched.

## Differences from the Python server

See the parity table in [protocol.md](protocol.md). The main behavioral
difference: `/api/v1/players` endpoints are fully authenticated and
ownership-scoped in Java (matching what the Android app needs), while the
Python v0.2 reference keeps its public/optional-auth synthetic players for
local development.

## OmniOS CE (SunOS/x86_64)

The bundled `org.xerial:sqlite-jdbc` jar has no SunOS native library; on
OmniOS a locally built `libsqlitejdbc.so` is loaded via the driver's
`org.sqlite.lib.path` hook (see `build.gradle`). Build it once with
`tools/omnios/build-sqlitejdbc.sh` — full instructions, pinned sources and
verification: [omnios.md](omnios.md). `./gradlew test` then runs unchanged;
without the library the test task fails fast with a pointer to the docs.

## Explicit non-goals

Same as the rest of MRR: no Minion Rush protocol compatibility, no game
assets, no secrets in the repo, no connections to production game servers —
local/synthetic test data only.
