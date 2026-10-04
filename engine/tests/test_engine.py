from fastapi.testclient import TestClient

from dpub_engine.main import app

client = TestClient(app)

SOURCES = [{"name": f"S{i}", "status": "OK", "qualityScore": 90} for i in range(9)]


def test_quality_blocks_on_late_source():
    body = {"sources": SOURCES + [{"name": "异地就医", "status": "LATE", "qualityScore": 93}],
            "orgs": [{"org": "某肛肠专科医院", "comorbidityPct": 41, "listQcPct": 82.0}]}
    r = client.post("/v1/quality/check", json=body).json()
    assert r["passed"] is False
    assert r["timelinessPct"] == 90.0
    assert r["blocking"] == ["异地就医"]
    assert r["flags"][0] == {"org": "某肛肠专科医院", "comorbidityLow": True, "listQcLow": True}


def test_quality_passes_when_all_arrived():
    body = {"sources": SOURCES + [{"name": "异地就医", "status": "OK", "qualityScore": 93}],
            "orgs": [{"org": "示例市第一人民医院", "comorbidityPct": 78, "listQcPct": 98.1}]}
    r = client.post("/v1/quality/check", json=body).json()
    assert r["passed"] is True and r["timelinessPct"] == 100.0


def test_behavior_multipliers_match_design():
    rows = [
        {"name": "入院72小时内重复检查", "ratePct": 27.4, "withAvg": 15680, "withoutAvg": 12140},
        {"name": "使用高值耗材", "ratePct": 18.2, "withAvg": 19860, "withoutAvg": 12450},
        {"name": "转入ICU", "ratePct": 6.3, "withAvg": 31240, "withoutAvg": 13150},
    ]
    r = client.post("/v1/topic/behaviors", json={"rows": rows}).json()
    assert [x["multiplier"] for x in r["rows"]] == [1.29, 1.6, 2.38]
    assert [x["level"] for x in r["rows"]] == ["elevated", "high", "high"]
    assert r["topMultiplier"] == "转入ICU"


def test_waterfall():
    r = client.post("/v1/topic/waterfall", json={"cityMean": 8920, "patientDiff": 1850, "behaviorDiff": 3010}).json()
    assert [b["value"] for b in r["bars"]] == [8920, 1850, 3010, 13780]
    assert r["bars"][2]["base"] == 10770
    assert r["patientSharePct"] == 38 and r["behaviorSharePct"] == 62
    assert r["axisMax"] >= 13780


def test_draft_and_validation():
    facts = {"code": "BR25", "optimization": {"totalWan": 1286}}
    r = client.post("/v1/topic/draft", json={"section": 6, "facts": facts}).json()
    assert "1,286 万元" in r["text"] and r["provider"] == "rule"
    bad = client.post("/v1/topic/draft", json={"section": 9, "facts": {}})
    assert bad.status_code == 422 and bad.json()["detail"].startswith("请求参数不合法")
