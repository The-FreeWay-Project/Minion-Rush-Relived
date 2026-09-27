from pathlib import Path

import pytest

from mrr.db import init_schema, open_connection
from mrr.services.players import (
    PlayerAlreadyExistsError,
    PlayerService,
    PlayerValidationError,
)


@pytest.fixture()
def service(tmp_path: Path) -> PlayerService:
    db_path = tmp_path / "service.db"
    with open_connection(db_path) as conn:
        init_schema(conn)
    return PlayerService(db_path)


def test_create_valid_player(service: PlayerService) -> None:
    player = service.create_player(
        player_id="synthetic-a",
        display_name="Synthetic A",
        level=3,
        coins=42,
    )
    assert player.player_id == "synthetic-a"
    assert player.display_name == "Synthetic A"
    assert player.level == 3
    assert player.coins == 42
    assert player.id >= 1


@pytest.mark.parametrize(
    ("kwargs", "message"),
    [
        ({"player_id": "", "display_name": "Ok"}, "player_id"),
        ({"player_id": "   ", "display_name": "Ok"}, "player_id"),
        ({"player_id": "ok", "display_name": ""}, "display_name"),
        ({"player_id": "ok", "display_name": "   "}, "display_name"),
        ({"player_id": "ok", "display_name": "Ok", "level": 0}, "level"),
        ({"player_id": "ok", "display_name": "Ok", "level": -5}, "level"),
        ({"player_id": "ok", "display_name": "Ok", "coins": -1}, "coins"),
    ],
)
def test_create_rejects_invalid_input(service: PlayerService, kwargs, message: str) -> None:
    with pytest.raises(PlayerValidationError, match=message):
        service.create_player(**kwargs)


def test_get_player(service: PlayerService) -> None:
    created = service.create_player(player_id="synthetic-b", display_name="Synthetic B")
    loaded = service.get_player("synthetic-b")
    assert loaded == created
    assert service.get_player_by_id(created.id) == created


def test_get_unknown_player_returns_none(service: PlayerService) -> None:
    assert service.get_player("nope") is None
    assert service.get_player_by_id(12345) is None


def test_delete_player(service: PlayerService) -> None:
    service.create_player(player_id="synthetic-c", display_name="Synthetic C")
    assert service.delete_player("synthetic-c") is True
    assert service.get_player("synthetic-c") is None
    assert service.delete_player("synthetic-c") is False


def test_duplicate_player_id_rejected(service: PlayerService) -> None:
    service.create_player(player_id="dup", display_name="First")
    with pytest.raises(PlayerAlreadyExistsError):
        service.create_player(player_id="dup", display_name="Second")


def test_service_uses_given_database_only(tmp_path: Path) -> None:
    db_path = tmp_path / "isolated-service.db"
    with open_connection(db_path) as conn:
        init_schema(conn)
    service = PlayerService(db_path)
    player = service.create_player(player_id="iso", display_name="Isolated")
    assert service.database_path == db_path
    assert service.get_player("iso") == player
    with open_connection(db_path) as conn:
        count = conn.execute("SELECT COUNT(*) FROM players").fetchone()[0]
    assert count == 1
