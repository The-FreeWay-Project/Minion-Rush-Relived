import sqlite3
from pathlib import Path

import pytest

from mrr.db import init_schema, open_connection
from mrr.repositories.players import Player, PlayerRepository


@pytest.fixture()
def repository(tmp_path: Path) -> PlayerRepository:
    db_path = tmp_path / "players.db"
    with open_connection(db_path) as conn:
        init_schema(conn)
    return PlayerRepository(db_path)


def test_create_player(repository: PlayerRepository) -> None:
    player = repository.create_player(
        player_id="synthetic-1",
        display_name="Synthetic One",
    )
    assert isinstance(player, Player)
    assert player.id == 1
    assert player.player_id == "synthetic-1"
    assert player.display_name == "Synthetic One"
    assert player.level == 1
    assert player.coins == 0
    assert player.created_at


def test_get_player_by_id(repository: PlayerRepository) -> None:
    created = repository.create_player(player_id="synthetic-2", display_name="Two", level=5, coins=10)
    loaded = repository.get_player_by_id(created.id)
    assert loaded == created


def test_get_player_by_player_id(repository: PlayerRepository) -> None:
    created = repository.create_player(player_id="synthetic-3", display_name="Three")
    loaded = repository.get_player_by_player_id("synthetic-3")
    assert loaded == created


def test_unknown_player_returns_none(repository: PlayerRepository) -> None:
    assert repository.get_player_by_id(999) is None
    assert repository.get_player_by_player_id("does-not-exist") is None


def test_delete_player(repository: PlayerRepository) -> None:
    repository.create_player(player_id="synthetic-4", display_name="Four")
    assert repository.delete_player("synthetic-4") is True
    assert repository.get_player_by_player_id("synthetic-4") is None
    assert repository.delete_player("synthetic-4") is False


def test_player_id_is_unique(repository: PlayerRepository) -> None:
    repository.create_player(player_id="duplicate", display_name="First")
    with pytest.raises(sqlite3.IntegrityError):
        repository.create_player(player_id="duplicate", display_name="Second")
    assert repository.get_player_by_player_id("duplicate").display_name == "First"


def test_list_players(repository: PlayerRepository) -> None:
    for i in range(3):
        repository.create_player(player_id=f"listed-{i}", display_name=f"Listed {i}")
    players = repository.list_players()
    assert [p.player_id for p in players] == ["listed-0", "listed-1", "listed-2"]
    assert repository.list_players(limit=1) == players[:1]
