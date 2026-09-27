from pathlib import Path

from mrr.__main__ import main


def test_init_db_cli_creates_database(tmp_path: Path, monkeypatch) -> None:
    db_path = tmp_path / "cli.db"
    monkeypatch.setenv("MRR_DATABASE_PATH", str(db_path))
    assert main(["init-db"]) == 0
    assert db_path.exists()


def test_init_db_cli_is_repeatable(tmp_path: Path, monkeypatch) -> None:
    db_path = tmp_path / "cli.db"
    monkeypatch.setenv("MRR_DATABASE_PATH", str(db_path))
    assert main(["init-db"]) == 0
    assert main(["init-db"]) == 0
