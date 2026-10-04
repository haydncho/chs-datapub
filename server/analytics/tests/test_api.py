"""HTTP layer with the database loaders stubbed out."""

from datetime import date

import pytest
from fastapi.testclient import TestClient

from app import db, main
from tests.test_alerts import ORGS, RULES, demo_series
from tests.test_drg import G


@pytest.fixture
def client(monkeypatch):
    seen = {}

    def load_series(since=None):
        seen["since"] = since
        return demo_series()

    monkeypatch.setattr(db, "load_groups", lambda: list(G))
    monkeypatch.setattr(db, "load_rules", lambda: list(RULES))
    monkeypatch.setattr(db, "load_series", load_series)
    monkeypatch.setattr(db, "load_orgs", lambda: dict(ORGS))
    monkeypatch.setattr(db, "load_alert_state", lambda: {("某肛肠专科医院", "GG19 次均总费用"): ("AL-01", "unsent")})
    monkeypatch.setattr(db, "latest_series_period", lambda: date(2026, 9, 1))
    c = TestClient(main.app)
    c.seen = seen
    return c


def test_evaluate_endpoint(client):
    r = client.get("/analytics/alerts/evaluate")
    assert r.status_code == 200
    body = r.json()
    assert body["periodKey"] == "2026-09" and body["ruleCount"] == 6 and len(body["alerts"]) == 6
    assert body["alerts"][0]["id"] == "AL-01"
    assert body["computedAt"] and body["batch"].startswith("202609-")
    assert client.seen["since"] == date(2025, 9, 1)  # 12 months + the month before for 环比


def test_evaluate_endpoint_period_param(client):
    assert client.get("/analytics/alerts/evaluate?period=2026-08").json()["periodKey"] == "2026-08"
    assert client.get("/analytics/alerts/evaluate?period=2026-8").status_code == 422
    assert client.get("/analytics/alerts/evaluate?period=2026-13").status_code == 422


def test_recommend_endpoint(client):
    r = client.get("/analytics/topics/recommend?top=2")
    assert r.status_code == 200
    items = r.json()
    assert [t["id"] for t in items] == ["T-BR25", "T-FM19"]
    assert len(items) == 2 and items[0]["batch"].startswith("202609-")
    assert client.get("/analytics/topics/recommend?top=0").status_code == 422


def test_panorama_and_health(client):
    assert client.get("/analytics/health").json() == {"status": "ok"}
    assert client.get("/analytics/drg/panorama").json()["groups"][0]["code"] == "BR25"
