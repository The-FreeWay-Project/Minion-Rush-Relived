# MRR API

> **Experimental.** The MRR API is in early development and is **subject to
> change** without notice. Nothing documented here is stable.
>
> **This is an internal/synthetic test API.** It manages local test players
> only. It is **not** a compatible Minion Rush server implementation and has
> no relationship to real Minion Rush accounts, protocol or data.

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

## Data Representation

All player endpoints share the same JSON representation:

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
| `player_id` | string | Public MRR test id, **unique**. This is the value used in URLs. |
| `display_name` | string | Display name of the synthetic test player. |
| `level` | integer | `>= 1`, default `1`. |
| `coins` | integer | `>= 0`, default `0`. |
| `created_at` | string | UTC ISO-8601 timestamp. |

URL paths always address players by **`player_id`** (the public test id),
never by the internal `id`.

## Endpoints

### `GET /api/v1/health`

| Item | Value |
|---|---|
| Method | `GET` |
| Success status | `200 OK` |

```json
{
    "status": "ok",
    "service": "mrr-server"
}
```

---

### `POST /api/v1/players`

Creates a synthetic test player.

| Item | Value |
|---|---|
| Method | `POST` |
| Content-Type | `application/json` |
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

**Status codes**

| Code | Meaning |
|---|---|
| `201 Created` | Player created; response body contains the player. |
| `409 Conflict` | `player_id` already exists. |
| `422 Unprocessable Entity` | Invalid request data (empty `player_id`/`display_name`, `level < 1`, `coins < 0`, missing/wrong fields). |

---

### `GET /api/v1/players/{player_id}`

Loads one player by its public `player_id`.

| Item | Value |
|---|---|
| Method | `GET` |
| Success status | `200 OK` |

**Status codes**

| Code | Meaning |
|---|---|
| `200 OK` | Player found; response body contains the player. |
| `404 Not Found` | No player with that `player_id`. |

---

### `DELETE /api/v1/players/{player_id}`

Deletes one player by its public `player_id`.

| Item | Value |
|---|---|
| Method | `DELETE` |
| Success status | `204 No Content` (empty body) |

**Status codes**

| Code | Meaning |
|---|---|
| `204 No Content` | Player deleted. |
| `404 Not Found` | No player with that `player_id`. |

---

### `GET /api/v1/players`

Lists synthetic test players ordered by internal id.

| Item | Value |
|---|---|
| Method | `GET` |
| Success status | `200 OK` |

**Response:** JSON array of player objects; `[]` when empty.
Currently capped at 100 entries (service default limit).

```json
[
    {
        "id": 1,
        "player_id": "test-001",
        "display_name": "TestMinion",
        "level": 10,
        "coins": 5000,
        "created_at": "2026-09-27T10:06:22+00:00"
    }
]
```

---

Unknown paths return `404 Not Found`. Expected validation, duplicate and
not-found cases never return `500`.
