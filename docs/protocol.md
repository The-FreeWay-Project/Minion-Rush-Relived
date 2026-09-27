# MRR Protocol (API v1)

> **Experimental.** This document defines the wire contract that both MRR
> server implementations — the Python/FastAPI reference (**v0.2**) and the
> Java/Spring Boot server (**v0.3**) — speak. It is MRR's own API. It is
> **not** the original Minion Rush backend protocol and claims no
> compatibility with it.

Endpoint-by-endpoint details live in [api.md](api.md). This document adds the
cross-implementation contract and the known differences between the two
servers.

## Conventions

- Base path: `/api/v1`.
- Request and response bodies are UTF-8 JSON with **snake_case** field names.
- Timestamps are ISO-8601 UTC with second precision, e.g.
  `2026-09-27T10:06:22+00:00` (Python) / `2026-09-27T10:06:22Z` (Java).
  Both formats parse as the same instant; clients must accept either.
- All errors use the body shape `{"detail": "<message>"}`.
- Authentication: `Authorization: Bearer <access_token>`; opaque tokens,
  hashed with SHA-256 server-side, TTL 86400 s by default.

## Endpoints and status codes

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/v1/health` | — | `200` | — |
| `POST` | `/api/v1/auth/register` | — | `201` | `409`, `415`, `422` |
| `POST` | `/api/v1/auth/login` | — | `200` | `401`, `415`, `422` |
| `POST` | `/api/v1/auth/logout` | Bearer | `204` | `401` |
| `GET` | `/api/v1/profile` | Bearer | `200` | `401` |
| `GET` | `/api/v1/player/state` | Bearer | `200` | `401`, `404` |
| `PUT` | `/api/v1/player/state` | Bearer | `200` | `401`, `404`, `422` |
| `GET` | `/api/v1/players` | see below | `200` | `401` (Java only) |
| `POST` | `/api/v1/players` | see below | `201` | `401`, `409`, `422` |
| `GET` | `/api/v1/players/{player_id}` | see below | `200` | `401`, `404` |
| `DELETE` | `/api/v1/players/{player_id}` | see below | `204` | `401`, `404` |

Unknown paths → `404 {"detail":"Not Found"}`; wrong HTTP method →
`405 {"detail":"Method Not Allowed"}`; wrong content type →
`415 {"detail":"Unsupported Media Type"}`; malformed JSON →
`422`. Login failure (`Invalid username or password`) is byte-identical for
unknown username and wrong password.

## Security contract (identical in both servers)

- Passwords: PBKDF2-HMAC-SHA256, encoded as
  `pbkdf2_sha256$<iterations>$<salt_hex>$<hash_hex>` (600k iterations at
  runtime), constant-time verification.
- Sessions: random bearer token; only `SHA-256(token)` is stored, plus
  `created_at`/`expires_at`; expired sessions are rejected with `401` and
  deleted.
- `401` responses carry `WWW-Authenticate: Bearer`.
- Clients must never log or persist tokens outside secure storage.

## Known differences: Python v0.2 vs Java v0.3

| Topic | Python (reference) | Java |
|---|---|---|
| `GET/POST /api/v1/players` | Public/optional auth; creates **unowned** synthetic players visible to everyone (v0.2 dev convenience). | Bearer auth **required**; players are created **owned** by the caller's account; foreign/missing → `404`. |
| `GET/DELETE /api/v1/players/{player_id}` | Unowned players are public; owned players require the owner's token (else `401`/`404`). | Always require the owner's token; anyone else → `404`. |
| Player JSON (`/players` endpoints) | Includes `level` and `coins` on the player object. | Has no `level`/`coins` on the player object; save state is read via `/player/state`. Profile still reports `level`/`coins` for the primary player. |
| Validation detail text | FastAPI phrasing (`Input should be greater than or equal to 1`, ...). | Custom phrasing (`level must be greater than or equal to 1`, ...). Same status (`422`) and body shape. |
| `profile.player` | Primary player incl. `level`/`coins` composed from `player_state`. | Same behavior — composed from `player_state`. |
| Session/auth messages | `Not authenticated` (missing header) vs `Invalid or expired session token` (bad token). | Identical messages. |
| Database | Own schema management (`python -m mrr init-db`), same logical tables. | JPA/Hibernate `ddl-auto=update` on the same logical model (see [database.md](database.md)). |

The Android client targets the **Java** semantics for `/players` (all calls
authenticated). Anything client-visible that differs per server is documented
in [android-client.md](android-client.md).

## Explicit non-goals

- No Minion Rush game protocol, message formats, encryption or Gameloft
  service behavior.
- No DRM, certificate pinning, signature or anti-tamper handling.
- No real user data, real credentials or connections to production game
  servers.
