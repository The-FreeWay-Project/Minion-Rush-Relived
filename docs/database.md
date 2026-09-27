# MRR Database

> **Experimental.** The database layout is in early development and subject to
> change.

## Location

The SQLite database file defaults to:

```
./data/mrr.db
```

The path is relative to the directory where the command is executed and can be
overridden with the `MRR_DATABASE_PATH` environment variable (see
`mrr/config.py`). The parent directory is created automatically when the
database is opened.

The database file is **git-ignored** and must never be committed.

## Initialization

The database is **not** created automatically when the server starts. Run the
CLI command once (and again after schema changes):

```bash
python -m mrr init-db
```

This opens a short-lived connection, creates missing tables, applies additive
migrations and closes the connection. It is idempotent: running it repeatedly
does not drop or modify existing data.

For a local development account with a randomly generated password:

```bash
python -m mrr create-dev-account my-dev-user
```

The password is printed once to the terminal and never stored in plaintext.

## Current Schema (MRR v0.2)

### Table `players`

Synthetic local test players. An optional `account_id` links a player to the
account that owns it (`NULL` = unowned public test player).

| Column | Type | Constraints | Default |
|---|---|---|---|
| `id` | INTEGER | PRIMARY KEY | — |
| `player_id` | TEXT | UNIQUE NOT NULL | — |
| `display_name` | TEXT | NOT NULL | — |
| `level` | INTEGER | NOT NULL | `1` |
| `coins` | INTEGER | NOT NULL | `0` |
| `created_at` | TEXT | NOT NULL | — |
| `account_id` | INTEGER | FK → `accounts.id` | `NULL` |

### Table `accounts`

Local development accounts.

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY |
| `username` | TEXT | UNIQUE NOT NULL |
| `password_hash` | TEXT | NOT NULL |
| `created_at` | TEXT | NOT NULL |

`password_hash` is a PBKDF2-HMAC-SHA256 hash of the form
`pbkdf2_sha256$<iterations>$<salt_hex>$<hash_hex>` — passwords are **never**
stored in plaintext.

### Table `sessions`

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY |
| `account_id` | INTEGER | NOT NULL, FK → `accounts.id` |
| `token_hash` | TEXT | UNIQUE NOT NULL |
| `created_at` | TEXT | NOT NULL |
| `expires_at` | TEXT | NOT NULL |

`token_hash` is the SHA-256 hash of the bearer token — the token itself is
never stored.

### Table `player_state`

Persistent save state for a player (foreign key to `players.id`,
`ON DELETE CASCADE`).

| Column | Type | Constraints | Default |
|---|---|---|---|
| `player_id` | INTEGER | PRIMARY KEY, FK → `players.id` | — |
| `experience` | INTEGER | NOT NULL | `0` |
| `level` | INTEGER | NOT NULL | `1` |
| `coins` | INTEGER | NOT NULL | `0` |
| `updated_at` | TEXT | NOT NULL | — |

## Migration

Databases created before MRR v0.2 lack `players.account_id`.
`init_schema()` detects this and runs:

```sql
ALTER TABLE players ADD COLUMN account_id INTEGER REFERENCES accounts(id);
```

Existing rows keep `account_id = NULL` (unowned). The migration is additive —
no data is lost and it can run repeatedly.

Foreign keys are enabled on every connection (`PRAGMA foreign_keys = ON`).

## Java Server (MRR v0.3)

The Java server uses the **same logical schema** (`accounts`, `sessions`,
`players`, `player_state`) via JPA/Hibernate with SQLite
(`hibernate-community-dialects`). Differences:

- The file lives at `server/java/data/mrr.db` (git-ignored) and is created
  **automatically** on first start (`ddl-auto=update`) — no `init-db` step.
- Column types/precision are Hibernate-generated (e.g. timestamps as TEXT
  ISO-8601 with `Z`); values are still ISO-8601 UTC.
- `Player` stores `player_id` (public string id) plus owner `account_id`;
  it intentionally has **no** `level`/`coins` — those live only in
  `player_state` (PK = `player_id`), and profile responses compose them from
  there.
- Sessions store only `SHA-256(token)` + expiry, and passwords use the same
  `pbkdf2_sha256$...` encoding, so hashes are interchangeable in principle
  (the two servers do not share databases in practice).

## Scope

This database is intended **exclusively** for local/synthetic test data: test
players, throwaway development accounts, their sessions and their save state.
It stores no real player information, no real credentials and no Minion Rush
game content.
