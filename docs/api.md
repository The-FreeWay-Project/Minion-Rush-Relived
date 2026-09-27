# MRR API

> **Experimental.** The MRR API is in early development and is **subject to
> change** without notice. Nothing documented here is stable.
>
> **This is an internal/synthetic test API.** It manages local test players
> and local development accounts only. It is **not** a compatible Minion Rush
> server implementation and has no relationship to real Minion Rush accounts,
> protocol or data.

> **Two implementations (v0.3).** This page documents the contract as served
> by the Python reference server; the Java server implements the same
> contract with ownership-scoped `/players` endpoints. Known differences:
> [protocol.md](protocol.md).

## Versioning Strategy

- All endpoints are grouped under a URL prefix that carries the API major
  version: `/api/v1/`.
- Breaking changes to existing endpoints result in a new version prefix
  (`/api/v2/`, ...), leaving the previous version in place during migration.
- Non-breaking additions (new endpoints, new optional fields) may be made
  within `/api/v1/` at any time.

## Base Path

```
/api/v1
```

The FastAPI application mounts all versioned routers under this prefix.
Interactive documentation (Swagger UI) is available at `/api/docs`.

## Authentication

Protected endpoints use a bearer token:

```http
Authorization: Bearer <access_token>
```

- Obtain a token via `POST /api/v1/auth/login`.
- Tokens are random, expire after `expires_in` seconds (default 86400) and
  are stored server-side **only as a SHA-256 hash**.
- Passwords are hashed with PBKDF2-HMAC-SHA256 (600k iterations, per-password
  salt) and never stored, returned or logged in plaintext.
- Invalid/expired/missing tokens → `401 Unauthorized` with
  `WWW-Authenticate: Bearer`.

## Data Representation

Player objects share this JSON representation:

```json
{
    "id": 1,
    "player_id": "test-001",
    "display_name": "TestMinion",
    "level": 10,
    "coins": 5000,
    "created_at": "2026-09-27T10:06:22+00:00"
}
```

| Field | Type | Description |
|---|---|---|
| `id` | integer | Internal auto-increment row id (not stable/public). |
| `player_id` | string | Public MRR test id, **unique**. Used in URLs. |
| `display_name` | string | Display name of the synthetic test player. |
| `level` | integer | `>= 1`, default `1`. |
| `coins` | integer | `>= 0`, default `0`. |
| `created_at` | string | UTC ISO-8601 timestamp. |

URL paths always address players by **`player_id`**.

**Ownership:** players created by `POST /api/v1/players` without a token are
unowned and publicly readable (local development). Players attached to an
account (each registered account gets one automatically) are only readable
and deletable by their owner — anyone else gets `404`, unauthenticated
requests get `401`.

## Endpoints

### `GET /api/v1/health`

| Item | Value |
|---|---|
| Method | `GET` |
| Success status | `200 OK` |

```json
{ "status": "ok", "service": "mrr-server" }
```

---

### `POST /api/v1/auth/register`

Creates a local account (plus its default player).

| Item | Value |
|---|---|
| Method | `POST` |
| Success status | `201 Created` |

**Request**

```json
{ "username": "testuser", "password": "test-password" }
```

**Response**

```json
{ "account_id": 1, "username": "testuser" }
```

| Code | Meaning |
|---|---|
| `201 Created` | Account created (never returns the password). |
| `409 Conflict` | Username already taken. |
| `422 Unprocessable Entity` | Empty `username` or `password`, wrong types. |

---

### `POST /api/v1/auth/login`

| Item | Value |
|---|---|
| Method | `POST` |
| Success status | `200 OK` |

**Request**

```json
{ "username": "testuser", "password": "test-password" }
```

**Response**

```json
{ "access_token": "...", "token_type": "bearer", "expires_in": 86400 }
```

| Code | Meaning |
|---|---|
| `200 OK` | Credentials valid; a new session is created. |
| `401 Unauthorized` | Invalid credentials — the response is **identical** for unknown username and wrong password. |
| `422 Unprocessable Entity` | Missing/wrong field types. |

---

### `POST /api/v1/auth/logout`

Requires `Authorization: Bearer <token>`.

| Item | Value |
|---|---|
| Method | `POST` |
| Success status | `204 No Content` (empty body) |

| Code | Meaning |
|---|---|
| `204 No Content` | Session invalidated; the token is unusable afterwards. |
| `401 Unauthorized` | Missing, invalid or expired token. |

---

### `GET /api/v1/profile` (protected)

Returns the authenticated account and its primary player.

| Item | Value |
|---|---|
| Method | `GET` |
| Auth | Bearer token required |
| Success status | `200 OK` |

**Response**

```json
{
    "account_id": 1,
    "username": "testuser",
    "player": {
        "player_id": "player-0001",
        "display_name": "testuser",
        "level": 1,
        "coins": 0
    }
}
```

`player` is `null` when the account has no player.

| Code | Meaning |
|---|---|
| `200 OK` | Profile returned (always only the caller's own data). |
| `401 Unauthorized` | Missing, invalid or expired token. |

---

### `GET /api/v1/player/state` (protected)

Reads the save state of the caller's primary player; a default state row is
created on first read.

| Item | Value |
|---|---|
| Method | `GET` |
| Auth | Bearer token required |
| Success status | `200 OK` |

```json
{
    "player_id": 1,
    "experience": 250,
    "level": 3,
    "coins": 1500,
    "updated_at": "2026-09-27T12:11:57+00:00"
}
```

| Code | Meaning |
|---|---|
| `200 OK` | State returned. |
| `401 Unauthorized` | Missing, invalid or expired token. |
| `404 Not Found` | The account has no player. |

---

### `PUT /api/v1/player/state` (protected)

Replaces the save state of the caller's primary player. Only the owner can
read or write their own state.

| Item | Value |
|---|---|
| Method | `PUT` |
| Auth | Bearer token required |
| Success status | `200 OK` |

**Request**

```json
{ "experience": 250, "level": 3, "coins": 1500 }
```

**Response:** the full state object (see `GET` above).

| Code | Meaning |
|---|---|
| `200 OK` | State stored; response contains the updated values. |
| `401 Unauthorized` | Missing, invalid or expired token. |
| `404 Not Found` | The account has no player. |
| `422 Unprocessable Entity` | `experience < 0`, `level < 1` or `coins < 0`. |

---

### `POST /api/v1/players`

Creates an unowned synthetic test player (public/local development).

| Item | Value |
|---|---|
| Method | `POST` |
| Success status | `201 Created` |

**Request body**

```json
{
    "player_id": "test-001",
    "display_name": "TestMinion",
    "level": 10,
    "coins": 5000
}
```

`level` and `coins` are optional and default to `1` and `0`.

| Code | Meaning |
|---|---|
| `201 Created` | Player created; response body contains the player. |
| `409 Conflict` | `player_id` already exists. |
| `422 Unprocessable Entity` | Invalid request data. |

---

### `GET /api/v1/players/{player_id}`

Loads one player by its public `player_id`.

| Code | Meaning |
|---|---|
| `200 OK` | Player found (unowned players are public). |
| `401 Unauthorized` | Target player is owned and no valid token was sent. |
| `404 Not Found` | No such player, or the player belongs to another account. |

---

### `DELETE /api/v1/players/{player_id}`

| Code | Meaning |
|---|---|
| `204 No Content` | Player deleted. |
| `401 Unauthorized` | Target player is owned and no valid token was sent. |
| `404 Not Found` | No such player, or the player belongs to another account. |

---

### `GET /api/v1/players`

Lists synthetic test players ordered by internal id.

`200 OK` with a JSON array ( `[]` when empty; capped at 100 entries).

---

Unknown paths return `404 Not Found`. Expected validation, duplicate,
not-found and authentication cases never return `500`.

## MRR Protocol

The endpoints above form **MRR's own API**. MRR does **not** claim to be
compatible with the original Minion Rush backend API, and it does not
implement any Minion Rush protocol, encryption or proprietary message format.
Any future compatibility work would be documented separately.
