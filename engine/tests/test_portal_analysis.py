"""机构门户分析（B2 / B3）引擎单测：分位、排名、匿名编号、小样本抑制、病组差距。"""

from fastapi.testclient import TestClient

from dpub_engine.main import app
from dpub_engine.routers.portal_analysis import percentile_rank, quantile

client = TestClient(app)


def items(vals: dict[str, float]):
    return [{"id": k, "value": v} for k, v in vals.items()]


# 种子数据：市三级同级组 6 家，本院 = H1
COST = {"H1": 12860, "H2": 10120, "H3": 13607, "H4": 10833, "H5": 11420, "H6": 14580}
QC = {"H2": 98.3, "H3": 97.4, "H1": 96.4, "H4": 96.2, "H5": 95.1, "H6": 94.0}
CEI = {"H1": 1.04, "H2": 0.88, "H3": 1.01, "H4": 0.95, "H5": 0.97, "H6": 1.12}


def test_quantile_and_rank_are_inverse():
    vals = sorted(COST.values())
    assert round(quantile(vals, 0.25)) == 10980
    assert quantile(vals, 0.5) == 12140
    assert round(quantile(vals, 0.75)) == 13420
    for p in (0, 20, 40, 60, 80, 100):
        assert round(percentile_rank(vals, quantile(vals, p / 100))) == p
    # 区间外截断、区间内插值
    assert percentile_rank(vals, 1) == 0 and percentile_rank(vals, 99999) == 100
    assert round(percentile_rank(vals, 12140), 6) == 50


def test_benchmark_percentile_tier():
    r = client.post("/v1/portal-analysis/benchmark", json={"items": items(COST), "ownId": "H1"}).json()
    assert r["suppressed"] is False and r["n"] == 6
    q = r["quantiles"]
    assert (round(q["p25"]), q["p50"], round(q["p75"])) == (10980, 12140, 13420)
    assert (q["p0"], q["p100"]) == (10120, 14580)
    assert r["ownPct"] == 60 and r["concern"] is False
    # 费用高为差：次均费用越低排名越前
    assert r["ownRank"] == 4
    assert "message" not in r


def test_concern_when_high_is_bad_and_ge_p70():
    r = client.post("/v1/portal-analysis/benchmark", json={"items": items(CEI), "ownId": "H1"}).json()
    assert r["ownPct"] == 80 and r["concern"] is True
    # 同一位置但「高为好」不标橙
    r = client.post("/v1/portal-analysis/benchmark", json={"items": items(CEI), "ownId": "H1", "higherIsBetter": True}).json()
    assert r["ownPct"] == 80 and r["concern"] is False and r["ownRank"] == 2


def test_anonymous_labels_follow_value_order():
    r = client.post("/v1/portal-analysis/benchmark", json={"items": items(QC), "ownId": "H1", "higherIsBetter": True}).json()
    assert [x["anonLabel"] for x in r["rows"]] == ["三级医院A", "三级医院B", "本院", "三级医院C", "三级医院D", "三级医院E"]
    assert [x["value"] for x in r["rows"]] == [98.3, 97.4, 96.4, 96.2, 95.1, 94.0]
    assert [x["own"] for x in r["rows"]].count(True) == 1


def test_named_ranking_direction_and_ties():
    r = client.post("/v1/portal-analysis/benchmark", json={"items": items(QC), "ownId": "H1", "higherIsBetter": True}).json()
    assert [x["id"] for x in r["ranking"]] == ["H2", "H3", "H1", "H4", "H5", "H6"]
    assert r["ownRank"] == 3
    tie = {"A": 1.0, "B": 2.0, "C": 2.0, "D": 3.0, "E": 4.0}
    r = client.post("/v1/portal-analysis/benchmark", json={"items": items(tie), "ownId": "C"}).json()
    assert [x["rank"] for x in r["ranking"]] == [1, 2, 2, 4, 5]
    assert r["ownRank"] == 2 and r["ownPct"] == 38  # 相同数值取平均位置 (1+2)/2 / 4


def test_small_peer_group_suppresses_percentile():
    few = {k: COST[k] for k in ("H1", "H2", "H3", "H4")}
    r = client.post("/v1/portal-analysis/benchmark", json={"items": items(few), "ownId": "H1"}).json()
    assert r["suppressed"] is True
    assert "quantiles" not in r and "ownPct" not in r
    assert r["message"] == "同级机构不足 5 家,不输出同级分位"


def test_unknown_own_id_is_rejected():
    r = client.post("/v1/portal-analysis/benchmark", json={"items": items(COST), "ownId": "X"})
    assert r.status_code == 422


def test_small_sample_groups_merged_into_other():
    groups = [
        {"code": "BR25", "cases": 253, "avgDiff": 2219},
        {"code": "BR11", "cases": 36, "avgDiff": 3472},
        {"code": "AH29", "cases": 12, "avgDiff": 1420},
        {"code": "IB39", "cases": 9, "avgDiff": -540},
        {"code": "NS15", "cases": 19, "avgDiff": -162},
        {"code": "JR15", "cases": 14, "avgDiff": 44},
    ]
    r = client.post("/v1/portal-analysis/small-sample", json={"groups": groups}).json()
    assert r["kept"] == ["BR25", "BR11"]
    assert r["merged"] == {"codes": ["AH29", "IB39", "NS15", "JR15"], "cases": 54, "avgDiff": 180}
    # 边界：恰好 30 例单独展示
    r = client.post("/v1/portal-analysis/small-sample", json={"groups": [{"code": "X", "cases": 30, "avgDiff": 1}]}).json()
    assert r["kept"] == ["X"] and r["merged"]["cases"] == 0 and r["merged"]["avgDiff"] == 0


def test_group_behaviors_and_gaps():
    body = {
        "costPct": 89,
        "behaviors": [
            {"name": "入院72小时内重复检查", "ownPct": 33, "peerMedianPct": 27},
            {"name": "使用高值耗材", "ownPct": 16, "peerMedianPct": 18},
            {"name": "康复治疗介入", "ownPct": 11, "peerMedianPct": 15, "higherIsWorse": False},
        ],
        "gaps": [
            {"name": "例均费用", "own": 15194, "bench": 12459},
            {"name": "平均住院日", "own": 10.5, "bench": 9.5, "decimals": 1},
            {"name": "药品占比", "own": 35, "bench": 28},
        ],
    }
    r = client.post("/v1/portal-analysis/group", json=body).json()
    assert r["costPctConcern"] is True
    assert [(b["deviation"], b["concern"]) for b in r["behaviors"]] == [(6, True), (-2, False), (-4, True)]
    assert [(g["gap"], g["worse"]) for g in r["gaps"]] == [(2735, True), (1.0, True), (7, True)]
    r = client.post("/v1/portal-analysis/group", json={**body, "costPct": 38, "gaps": [{"name": "x", "own": 1, "bench": 2}]}).json()
    assert r["costPctConcern"] is False and r["gaps"][0]["worse"] is False
