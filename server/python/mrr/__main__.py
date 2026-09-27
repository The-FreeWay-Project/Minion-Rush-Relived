"""Command-line entry point for MRR.

Usage:
    python -m mrr                          # start the development server
    python -m mrr serve                    # start the development server (explicit)
    python -m mrr init-db                  # create/upgrade the local SQLite database
    python -m mrr create-dev-account NAME  # create a local dev account (random password)
"""

from __future__ import annotations

import argparse
import secrets

from mrr.config import load_settings
from mrr.db import init_schema, open_connection
from mrr.services.accounts import AccountAlreadyExistsError, AccountService


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


def create_dev_account(username: str) -> int:
    """Create a local dev account with a randomly generated password.

    The password is printed once to stdout and never stored anywhere else.
    """
    settings = load_settings()
    with open_connection(settings.database_path) as conn:
        init_schema(conn)

    password = secrets.token_urlsafe(12)
    service = AccountService(settings.database_path)
    try:
        account = service.register(username=username, password=password)
    except AccountAlreadyExistsError:
        print(f"Username {username!r} already exists — nothing changed.")
        return 1

    print(f"Created dev account {account.username!r} (account_id={account.id}).")
    print(f"Generated password (shown once): {password}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="mrr", description="MRR server utilities")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("serve", help="start the development server (default)")
    sub.add_parser("init-db", help="initialize the local SQLite database")
    dev = sub.add_parser(
        "create-dev-account",
        help="create a local development account with a generated password",
    )
    dev.add_argument("username", help="username for the new dev account")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "init-db":
        return init_db()
    if args.command == "create-dev-account":
        return create_dev_account(args.username)
    run_server()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
