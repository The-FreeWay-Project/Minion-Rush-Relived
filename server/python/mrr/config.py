"""Configuration for the MRR server.

Defaults describe a local development setup. Every value can be overridden
with an ``MRR_*`` environment variable. No secrets are stored here.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

ENV_PREFIX = "MRR_"

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8000
DEFAULT_DATABASE_PATH = Path("./data/mrr.db")
DEFAULT_ENVIRONMENT = "development"

VALID_ENVIRONMENTS = ("development", "production")


@dataclass(frozen=True)
class Settings:
    host: str = DEFAULT_HOST
    port: int = DEFAULT_PORT
    database_path: Path = DEFAULT_DATABASE_PATH
    environment: str = DEFAULT_ENVIRONMENT


def load_settings(environ: Mapping[str, str] | None = None) -> Settings:
    """Build :class:`Settings` from environment variables, falling back to defaults.

    Recognised variables: ``MRR_HOST``, ``MRR_PORT``,
    ``MRR_DATABASE_PATH``, ``MRR_ENVIRONMENT``.
    """
    env = os.environ if environ is None else environ

    host = env.get(f"{ENV_PREFIX}HOST", DEFAULT_HOST)
    port_raw = env.get(f"{ENV_PREFIX}PORT", str(DEFAULT_PORT))
    db_raw = env.get(f"{ENV_PREFIX}DATABASE_PATH", str(DEFAULT_DATABASE_PATH))
    environment = env.get(f"{ENV_PREFIX}ENVIRONMENT", DEFAULT_ENVIRONMENT)

    try:
        port = int(port_raw)
    except ValueError as exc:
        raise ValueError(f"{ENV_PREFIX}PORT must be an integer, got {port_raw!r}") from exc
    if not 1 <= port <= 65535:
        raise ValueError(f"{ENV_PREFIX}PORT must be between 1 and 65535, got {port}")

    if environment not in VALID_ENVIRONMENTS:
        raise ValueError(
            f"{ENV_PREFIX}ENVIRONMENT must be one of {VALID_ENVIRONMENTS}, got {environment!r}"
        )

    return Settings(
        host=host,
        port=port,
        database_path=Path(db_raw),
        environment=environment,
    )
