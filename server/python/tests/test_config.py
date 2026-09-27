import pytest

from mrr.config import (
    DEFAULT_DATABASE_PATH,
    DEFAULT_ENVIRONMENT,
    DEFAULT_HOST,
    DEFAULT_PORT,
    Settings,
    load_settings,
)


def test_defaults() -> None:
    settings = load_settings(environ={})
    assert settings == Settings(
        host="127.0.0.1",
        port=8000,
        database_path=DEFAULT_DATABASE_PATH,
        environment="development",
    )
    assert settings.host == DEFAULT_HOST
    assert settings.port == DEFAULT_PORT
    assert settings.environment == DEFAULT_ENVIRONMENT


def test_defaults_match_documented_values() -> None:
    settings = load_settings(environ={})
    assert settings.database_path.name == "mrr.db"


def test_environment_variables_override_defaults() -> None:
    settings = load_settings(
        environ={
            "MRR_HOST": "0.0.0.0",
            "MRR_PORT": "9001",
            "MRR_DATABASE_PATH": "/tmp/other.db",
            "MRR_ENVIRONMENT": "production",
        }
    )
    assert settings.host == "0.0.0.0"
    assert settings.port == 9001
    assert settings.database_path.as_posix() == "/tmp/other.db"
    assert settings.environment == "production"


def test_environment_variables_read_from_os_environ(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MRR_HOST", "10.0.0.5")
    settings = load_settings()
    assert settings.host == "10.0.0.5"


def test_invalid_port_raises() -> None:
    with pytest.raises(ValueError, match="MRR_PORT"):
        load_settings(environ={"MRR_PORT": "not-a-number"})
    with pytest.raises(ValueError, match="MRR_PORT"):
        load_settings(environ={"MRR_PORT": "70000"})


def test_invalid_environment_raises() -> None:
    with pytest.raises(ValueError, match="MRR_ENVIRONMENT"):
        load_settings(environ={"MRR_ENVIRONMENT": "staging"})
