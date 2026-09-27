"""Command-line entry point for MRR.

Usage:
    python -m mrr            # start the development server
    python -m mrr serve      # start the development server (explicit)
    python -m mrr init-db    # create/upgrade the local SQLite database
"""

from __future__ import annotations

import argparse

from mrr.config import load_settings
from mrr.db import init_schema, open_connection


def run_server() -> None:
    import uvicorn

    settings = load_settings()
    uvicorn.run("mrr.api.main:app", host=settings.host, port=settings.port)


def init_db() -> int:
    settings = load_settings()
    with open_connection(settings.database_path) as conn:
        init_schema(conn)
    print(f"Initialized database at {settings.database_path}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="mrr", description="MRR server utilities")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("serve", help="start the development server (default)")
    sub.add_parser("init-db", help="initialize the local SQLite database")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "init-db":
        return init_db()
    run_server()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
