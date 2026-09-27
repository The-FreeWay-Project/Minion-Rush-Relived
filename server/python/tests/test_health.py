from fastapi.testclient import TestClient

from mrr.api.main import app

client = TestClient(app)


def test_application_imports() -> None:
    assert app is not None
    assert app.title == "MRR"


def test_health_returns_200() -> None:
    response = client.get("/api/v1/health")
    assert response.status_code == 200


def test_health_payload() -> None:
    response = client.get("/api/v1/health")
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "mrr-server"


def test_unknown_endpoint_returns_404() -> None:
    response = client.get("/api/v1/does-not-exist")
    assert response.status_code == 404
