"""指标配置与智能推荐（A4 / A6）引擎单测：公式校验、机构身份预览裁剪、选题得分、归因、标杆分组。"""

from fastapi.testclient import TestClient

from dpub_engine.main import app
from dpub_engine.routers.indicator import percentile_rank, quantile

client = TestClient(app)

ATOMS = [{"name": "术前住院天数", "cases": 18420}, {"name": "手术出院人次", "cases": 18420}, {"name": "住院总费用", "cases": 41260}]
DIMS = ["机构", "等级", "病组", "县区", "时间", "险种"]
FIELDS = ["手术标志", "离院方式", "险种"]
DESIGN_FORMULA = """术前平均住院日 =
  SUM(术前住院天数)
  / COUNT(手术出院人次)
WHERE 手术标志 = 1 AND 离院方式 != '死亡'
GROUP BY 机构, 等级, 病组, 时间"""


def validate(formula: str, dims=("机构", "等级", "病组", "时间")):
    body = {"formula": formula, "atoms": ATOMS, "dims": list(dims), "allDims": DIMS, "filterFields": FIELDS, "caliber": "结算清单 v2026.1"}
    r = client.post("/v1/indicator/formula/validate", json=body)
    assert r.status_code == 200
    return r.json()


# ---------------------------------------------------------------- 分位


def test_weibull_quantiles_match_design():
    city3 = [1.6, 2.0, 2.1, 2.5, 2.8, 3.2]
    assert [round(quantile(city3, q), 1) for q in (0.25, 0.5, 0.75)] == [1.9, 2.3, 2.9]
    assert percentile_rank(city3, 2.8) == 71
    assert quantile([5.0], 0.5) == 5.0
    assert percentile_rank([1, 2, 3], 0.5) == 0 and percentile_rank([1, 2, 3], 9) == 100


# ---------------------------------------------------------------- 公式校验


def test_design_formula_passes_with_estimated_cases():
    r = validate(DESIGN_FORMULA)
    assert r["ok"] is True and r["errors"] == []
    assert r["name"] == "术前平均住院日"
    assert r["atoms"] == ["术前住院天数", "手术出院人次"]
    assert r["groupBy"] == ["机构", "等级", "病组", "时间"]
    assert r["estimatedCases"] == 18420
    assert r["message"] == "✓ 语法校验通过 · 引用口径:结算清单 v2026.1 · 预计取数 18,420 例"


def test_unknown_atom_and_unselected_dim_reported_by_line():
    r = validate(DESIGN_FORMULA.replace("COUNT(手术出院人次)", "COUNT(手术人数)").replace("时间", "县区"))
    assert r["ok"] is False
    assert {(e["line"], e["message"]) for e in r["errors"]} == {(3, "未知原子指标「手术人数」"), (5, "维度「县区」未在左侧选中")}
    assert r["message"].startswith("✗ 第 3 行:未知原子指标「手术人数」")


def test_syntax_errors():
    assert validate("术前平均住院日 = SUM(术前住院天数")["errors"][0]["message"].startswith("缺少函数 SUM 的右括号")
    r = validate("术前平均住院日\n  SUM(术前住院天数)")
    assert r["errors"][0]["line"] == 2 and "等号" in r["errors"][0]["message"]
    r = validate("x = 术前住院天数 / COUNT(手术出院人次)")
    assert "须放在聚合函数中引用" in r["errors"][0]["message"]
    r = validate("x = SUM(术前住院天数)\nWHERE 床位 = 1")
    assert r["errors"][0] == {"line": 2, "message": "未知过滤字段「床位」"}
    r = validate("x = SUM(术前住院天数) @")
    assert "无法识别的字符" in r["errors"][0]["message"]
    assert validate("   ")["ok"] is False
    assert validate("x = 1 + 2")["errors"][0]["message"].startswith("表达式中至少需要一个聚合函数")
    assert validate("x = PCTL(住院总费用, 75)")["ok"] is True
    assert validate("x = PCTL(住院总费用, 175)")["ok"] is False


# ---------------------------------------------------------------- 以机构身份预览


def groups():
    def g(name, vals, first=None):
        orgs = [{"org": f"{name}{i}", "value": v, "cases": 100} for i, v in enumerate(vals)]
        if first:
            orgs[-1]["org"] = first
        return {"name": name, "orgs": orgs}

    return [g("市三级", [1.6, 2.0, 2.1, 2.5, 3.2, 2.8], "示例市第一人民医院"), g("县三级", [2.6, 2.9, 3.0, 2.1], "甲县人民医院"),
            g("一级", [1.0, 1.2, 1.5, 1.6, 2.0, 0.4], "小医院")]


def test_preview_org_only_gets_own_value_and_quantiles():
    r = client.post("/v1/indicator/preview", json={"viewerOrg": "示例市第一人民医院", "groups": groups()}).json()
    assert r["mode"] == "normal" and r["value"] == 2.8 and r["pct"] == 71
    assert (r["p25"], r["p50"], r["p75"]) == (1.9, 2.3, 2.9)
    assert r["peerCount"] == 6 and r["group"] == "市三级"
    # 不下发他院名称与数值
    assert "groups" not in r and "市三级0" not in str(r)


def test_preview_small_sample_suppressed():
    r = client.post("/v1/indicator/preview", json={"viewerOrg": "甲县人民医院", "groups": groups()}).json()
    assert r["mode"] == "suppressed" and r["value"] == 2.1 and "p50" not in r
    assert r["note"].startswith("同级组仅 4 家,低于抑制阈值 5 家")
    # 阈值放宽到 4 家后可输出
    r = client.post("/v1/indicator/preview", json={"viewerOrg": "甲县人民医院", "groups": groups(), "minOrgs": 4}).json()
    assert r["mode"] == "normal"
    # 本院病例数不足同样抑制
    gs = groups()
    gs[2]["orgs"][-1]["cases"] = 12
    r = client.post("/v1/indicator/preview", json={"viewerOrg": "小医院", "groups": gs}).json()
    assert r["mode"] == "suppressed" and "本院本期病例 12 例" in r["note"]


def test_preview_internal_returns_no_numbers():
    r = client.post("/v1/indicator/preview", json={"viewerOrg": "示例市第一人民医院", "groups": groups(), "internal": True}).json()
    assert r["mode"] == "internal" and "value" not in r and "p50" not in r


def test_preview_convener_full_view():
    r = client.post("/v1/indicator/preview", json={"groups": groups()}).json()
    assert r["mode"] == "all"
    rows = {g["name"]: g for g in r["groups"]}
    assert rows["县三级"]["suppressed"] is True and "p50" not in rows["县三级"]
    assert rows["市三级"]["p75"] == 2.9 and rows["市三级"]["n"] == 6
    assert r["totalCases"] == 1600


# ---------------------------------------------------------------- 选题


def factors(**kw):
    base = {"caseVolume": 50, "amount": 50, "trend": 50, "fundGap": 50, "attention": 50, "recommend": 50, "admitZ": 50}
    base.update(kw)
    return base


def test_topic_scores_weighted_and_reasons_sorted():
    rows = [
        {"code": "BR25", "name": "脑缺血", "factors": {"caseVolume": 98, "amount": 88, "trend": 84, "fundGap": 96, "attention": 94,
                                                      "recommend": 88, "admitZ": 88}},
        {"code": "KS15", "name": "糖尿病", "factors": factors(attention=98)},
    ]
    r = client.post("/v1/indicator/topic-scores", json={"rows": rows}).json()
    assert [x["code"] for x in r["rows"]] == ["BR25", "KS15"]
    assert r["rows"][0]["score"] == 92
    assert r["rows"][0]["reasons"] == ["病例量大", "基金差额大", "机构关注度高"]
    assert r["rows"][1]["score"] == 55 and r["rows"][1]["reasons"] == ["机构关注度高"]
    assert abs(sum(r["weights"].values()) - 1) < 1e-9


# ---------------------------------------------------------------- 归因


def test_attribution_split_and_withheld():
    rows = [
        {"code": "BR25", "name": "脑缺血", "patientDiff": 1847, "behaviorDiff": 3013, "comorbidityPct": 72, "cases": 1420,
         "behaviors": [{"name": "转入ICU", "amount": 540}, {"name": "入院72小时内重复检查", "amount": 1120}, {"name": "使用辅助用药", "amount": 860}]},
        {"code": "GG19", "name": "肛门", "patientDiff": 1, "behaviorDiff": 1, "comorbidityPct": 41, "cases": 640,
         "lowQcOrg": "某肛肠专科医院", "lowQcPct": 82.0},
    ]
    r = client.post("/v1/indicator/attribution", json={"rows": rows}).json()["rows"]
    assert r[0]["status"] == "ok" and r[0]["totalDiff"] == 4860
    assert (r[0]["patientPct"], r[0]["behaviorPct"]) == (38.0, 62.0)
    assert [b["name"] for b in r[0]["topBehaviors"]] == ["入院72小时内重复检查", "使用辅助用药"]
    assert r[1]["status"] == "withheld" and "patientPct" not in r[1]
    assert r[1]["reason"] == "原因:本组合并症编码率 41%,低于 60% 的输出门槛;某肛肠专科医院结算清单质控率 82%。编码质量改善后自动重算。"


# ---------------------------------------------------------------- 标杆


def test_benchmark_recomputes_with_threshold_and_small_groups():
    costs = [8000, 9000, 10000, 11000, 12000, 13000, 14000, 15000, 16000, 17000]
    big = {"name": "一级", "orgs": [{"avgCost": c, "qcPct": 96 if c < 10000 else 90} for c in costs]}
    small = {"name": "县三级", "orgs": [{"avgCost": 10000, "qcPct": 99}] * 4}
    rows = {}
    for th in (60, 75, 90):
        r = client.post("/v1/indicator/benchmark", json={"threshold": th, "groups": [big, small]}).json()
        rows[th] = r["rows"][0]
        assert r["rows"][1] == {"name": "县三级", "n": 4, "small": True}
        assert r["rule"].endswith(f"偏离组 = 例均费用 > P{th}")
    assert [rows[t]["deviant"] for t in (60, 75, 90)] == [4, 2, 1]
    assert all(rows[t]["bench"] + rows[t]["middle"] + rows[t]["deviant"] == 10 for t in rows)
    assert rows[75]["bench"] == 2
    assert client.post("/v1/indicator/benchmark", json={"threshold": 62, "groups": [big]}).status_code == 422
    assert client.post("/v1/indicator/benchmark", json={"threshold": 95, "groups": [big]}).status_code == 422
