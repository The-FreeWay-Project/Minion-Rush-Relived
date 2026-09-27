# Minion-Rush-Relived

**MRR** — A community-driven server project for Minion Rush.

> **Status: Early Development.** This repository currently contains only a
> minimal backend foundation. MRR does **not** yet support any Minion Rush
> gameplay, protocol, accounts, or game data of any kind.

## Technology Stack

- Python 3.11+
- FastAPI
- Uvicorn
- SQLite (via Python's standard library)
- pytest

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

## Configuration

Configuration lives in `server/python/mrr/config.py` with development
defaults. Every value can be overridden with an environment variable:

| Variable | Default | Description |
|---|---|---|
| `MRR_HOST` | `127.0.0.1` | Bind address |
| `MRR_PORT` | `8000` | Bind port |
| `MRR_DATABASE_PATH` | `./data/mrr.db` | SQLite database file |
| `MRR_ENVIRONMENT` | `development` | `development` or `production` |

No secrets are stored in the repository.

## Database

The database file defaults to `./data/mrr.db` (relative to the working
directory) and is **not** created automatically at server start. Initialize it
manually:

```bash
python -m mrr init-db
```

The current schema contains a single `players` table reserved for
**local/synthetic test data only**. See [docs/database.md](docs/database.md).

## Running the Tests

From the repository root:

```bash
pytest
```

Tests use temporary databases and never touch your local `data/mrr.db`.

## API

Base path: `/api/v1` (experimental, subject to change).

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/health` | Health check |
| `POST` | `/api/v1/players` | Create a synthetic test player (`201` / `409` / `422`) |
| `GET` | `/api/v1/players` | List test players (`200`, `[]` when empty) |
| `GET` | `/api/v1/players/{player_id}` | Get one player by public test id (`200` / `404`) |
| `DELETE` | `/api/v1/players/{player_id}` | Delete one player (`204` / `404`) |

Example health response:

```json
{
    "status": "ok",
    "service": "mrr-server"
}
```

> These player endpoints are an **internal/synthetic test API** for local test
> data only — not a Minion Rush compatible server implementation.

Interactive API docs are served at `/api/docs`. Full details:
[docs/api.md](docs/api.md).

## License

This project is licensed under the GNU Affero General Public License v3.0.
See [LICENSE](LICENSE) for the full text.
