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

This opens a short-lived connection, creates missing tables and closes the
connection. It is idempotent: running it repeatedly does not drop or modify
existing data.

## Current Schema

### Table `players`

Reserved for **synthetic local test players only**.

| Column | Type | Constraints | Default |
|---|---|---|---|
| `id` | INTEGER | PRIMARY KEY | — |
| `player_id` | TEXT | UNIQUE NOT NULL | — |
| `display_name` | TEXT | NOT NULL | — |
| `level` | INTEGER | NOT NULL | `1` |
| `coins` | INTEGER | NOT NULL | `0` |
| `created_at` | TEXT | NOT NULL | — |

Foreign keys are enabled on every connection. No other tables exist yet.

## Scope

This database is currently intended **exclusively** for local/synthetic test
data. It stores no real player information, no accounts, no credentials and no
Minion Rush game content.
