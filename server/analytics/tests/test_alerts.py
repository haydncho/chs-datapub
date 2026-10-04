from datetime import date

import pytest

from app.alerts import (
    Point, Rule, check, evaluate, format_threshold, format_value, latest_period, percentile,
)
from app.periods import add_months, batch_id, last_months

SEP = date(2026, 9, 1)
MONTHS = last_months(SEP, 12)  # 2025-10 … 2026-09


def series(org, metric, values, end=SEP):
    ms = last_months(end, len(values))
    return [Point(org, metric, m, v) for m, v in zip(ms, values)]


# A11 seed rules (same as the V5 migration)
R_MOM = Rule("R01", "GG19.cost_per_case", "GG19 次均总费用", "mom_pct", 15, "high", "万元 · 千元", "num", 1)
R_P90 = Rule("R02", "ES35.readmit14", "ES35 14天再住院率", "gt_p90", None, "high", "%", "pct", 1)
R_GT = Rule("R03", "BR25.border_ratio", "BR25 临界区病例占比", "gt", 35, "mid", "%", "pct", 0)
R_LT = Rule("R04", "qc_pass_rate", "结算清单质控率", "lt", 95, "mid", "%", "pct", 1)
R_P90B = Rule("R05", "oop_ratio", "医保外费用占比", "gt_p90", None, "mid", "%", "pct", 1)
R_YUAN = Rule("R06", "IU29.diff_per_case", "IU29 例均基金差额", "gt", 1500, "low", "元", "yuan", 0)

ORGS = {"H001": "示例市第一人民医院", "H002": "示例市第二人民医院", "H003": "示例市中医院",
        "H004": "示例市第三人民医院", "H010": "甲县人民医院", "H011": "乙县中医院",
        "H012": "乙县人民医院", "H020": "丙区第2医院", "H030": "某肛肠专科医院"}


def demo_series():
    s = []
    s += series("H030", R_MOM.metric, [7.1, 7.0, 7.3, 7.2, 7.4, 7.3, 7.5, 7.6, 7.4, 7.7, 7.6, 9.393])
    s += series("H001", R_MOM.metric, [7.8, 8.0])
    s += series("H010", R_P90.metric, [4.8, 5.1, 5.0, 5.4, 5.2, 5.6, 5.9, 6.0, 6.4, 6.8, 7.6, 8.7])
    for o, v in zip(["H001", "H002", "H003", "H004", "H011", "H030", "H012", "H020"],
                    [4.1, 4.5, 4.8, 5.0, 5.3, 5.6, 6.2, 6.2]):
        s += series(o, R_P90.metric, [v])
    s += series("H004", R_GT.metric, [28, 30, 29, 31, 33, 32, 34, 35, 36, 38, 39, 41])
    s += series("H001", R_GT.metric, [22]) + series("H002", R_GT.metric, [30])
    s += series("H012", R_LT.metric, [96.2, 96.0, 95.8, 95.9, 95.4, 95.6, 95.1, 94.8, 94.6, 94.2, 94.0, 93.8])
    s += series("H001", R_LT.metric, [98.6]) + series("H011", R_LT.metric, [95.4])
    s += series("H020", R_P90B.metric, [8.1, 8.4, 8.2, 8.9, 9.0, 9.3, 9.6, 9.8, 10.2, 10.6, 10.9, 11.2])
    for o, v in zip(["H001", "H002", "H003", "H004", "H010", "H011", "H012", "H030"],
                    [5.2, 6.0, 6.4, 6.9, 7.3, 8.1, 9.4, 9.4]):
        s += series(o, R_P90B.metric, [v])
    s += series("H003", R_YUAN.metric, [980, 1040, 1100, 1180, 1220, 1260, 1310, 1380, 1420, 1480, 1560, 1620])
    s += series("H001", R_YUAN.metric, [1210])
    return s


RULES = [R_MOM, R_P90, R_GT, R_LT, R_P90B, R_YUAN]


# ── percentile ────────────────────────────────────────────────────────────────

def test_percentile_linear_interpolation():
    assert percentile([1, 2, 3, 4, 5], 50) == 3
    assert percentile([1, 2, 3, 4], 90) == pytest.approx(3.7)
    assert percentile([7.0], 90) == 7.0
    assert percentile([5, 1, 3], 0) == 1 and percentile([5, 1, 3], 100) == 5


def test_percentile_rejects_bad_input():
    with pytest.raises(ValueError):
        percentile([], 90)
    with pytest.raises(ValueError):
        percentile([1, 2], 120)


# ── single-rule checks, one per kind ──────────────────────────────────────────

def test_mom_pct_rise():
    hit = check(R_MOM, 9.393, 7.6)
    assert hit is not None
    assert hit.shown == "+23.6%"
    assert hit.threshold_text == "环比 > 15%"
    assert hit.threshold_value == pytest.approx(7.6 * 1.15)
    assert check(R_MOM, 8.7, 7.6) is None  # +14.5%: below threshold
    assert check(R_MOM, 7.6 * 1.15, 7.6) is None  # exactly at threshold does not fire


def test_mom_pct_drop_rule():
    drop = Rule("R18", "avg_cost", "次均费用", "mom_pct", -20, "low", "千元", "num", 1)
    hit = check(drop, 7.0, 10.0)
    assert hit is not None and hit.shown == "-30.0%" and hit.threshold_text == "环比 < -20%"
    assert check(drop, 9.1, 9.6) is None
    assert check(drop, 15.0, 10.0) is None  # a rise never fires a drop rule


def test_mom_pct_needs_previous_month():
    assert check(R_MOM, 9.4, None) is None
    assert check(R_MOM, 9.4, 0) is None


def test_gt_p90_peer_group():
    peers = [4.1, 4.5, 4.8, 5.0, 5.3, 5.6, 6.2, 6.2]
    hit = check(R_P90, 8.7, None, peers)
    assert hit is not None
    assert (hit.shown, hit.threshold_text, hit.threshold_value) == ("8.7%", "> P90 (6.2%)", 6.2)
    assert check(R_P90, 6.2, None, peers) is None  # equal to P90 → no alert


def test_gt_p90_needs_enough_peers():
    assert check(R_P90, 99, None, [1, 2, 3, 4]) is None  # 4 < min_peers 5
    assert check(R_P90, 99, None, []) is None
    assert check(R_P90, 99, None, None) is None
    small = Rule("X", "m", "m", "gt_p90", None, "mid", min_peers=2)
    assert check(small, 99, None, [1, 2]) is not None


def test_gt_and_lt_absolute():
    hit = check(R_GT, 41, 39)
    assert hit is not None and (hit.shown, hit.threshold_text, hit.threshold_value) == ("41%", "> 35%", 35)
    assert check(R_GT, 35, None) is None
    lo = check(R_LT, 93.8)
    assert lo is not None and (lo.shown, lo.threshold_text) == ("93.8%", "< 95%")
    assert check(R_LT, 95, None) is None and check(R_LT, 98.6) is None


def test_yuan_formatting():
    hit = check(R_YUAN, 1620)
    assert hit is not None and (hit.shown, hit.threshold_text) == ("+1,620", "> +1,500 元")
    assert format_value(R_YUAN, -2150) == "−2,150"


def test_formatting_helpers():
    cmi = Rule("R17", "cmi", "CMI", "lt", 0.8, "low", "", "num", 2)
    assert format_value(cmi, 0.756) == "0.76"
    assert format_threshold(cmi) == "< 0.8"
    assert format_threshold(R_P90B, 9.4) == "> P90 (9.4%)"


def test_invalid_rules_never_fire():
    assert check(Rule("X", "m", "m", "gt", None, "low"), 10) is None
    assert check(Rule("X", "m", "m", "mom_pct", None, "low"), 10, 1) is None
    assert check(Rule("X", "m", "m", "between", 1, "low"), 10, 1) is None
    assert check(R_GT, None) is None


# ── full evaluation (A11 seed) ────────────────────────────────────────────────

def test_evaluate_reproduces_a11_seed():
    out = evaluate(RULES, demo_series(), ORGS)
    assert out["periodKey"] == "2026-09"
    assert out["months"] == ["10", "11", "12", "1", "2", "3", "4", "5", "6", "7", "8", "9"]
    assert out["ruleCount"] == 6
    got = [(a["org"], a["metric"], a["value"], a["threshold"], a["level"]) for a in out["alerts"]]
    assert got == [
        ("某肛肠专科医院", "GG19 次均总费用", "+23.6%", "环比 > 15%", "high"),
        ("甲县人民医院", "ES35 14天再住院率", "8.7%", "> P90 (6.2%)", "high"),
        ("示例市第三人民医院", "BR25 临界区病例占比", "41%", "> 35%", "mid"),
        ("乙县人民医院", "结算清单质控率", "93.8%", "< 95%", "mid"),
        ("丙区第2医院", "医保外费用占比", "11.2%", "> P90 (9.4%)", "mid"),
        ("示例市中医院", "IU29 例均基金差额", "+1,620", "> +1,500 元", "low"),
    ]
    gg = out["alerts"][0]
    assert gg["trend"] == [7.1, 7.0, 7.3, 7.2, 7.4, 7.3, 7.5, 7.6, 7.4, 7.7, 7.6, 9.4]
    assert gg["thresholdValue"] == 8.7 and gg["unit"] == "万元 · 千元"
    assert gg["id"] == "AL-R01-H030" and gg["status"] == "unsent"
    assert out["alerts"][1]["thresholdValue"] == 6.2
    br = out["alerts"][2]
    assert br["trend"][-1] == 41 and isinstance(br["trend"][-1], int) and isinstance(br["thresholdValue"], int)


def test_evaluate_keeps_core_alert_ids_and_status():
    existing = {("甲县人民医院", "ES35 14天再住院率"): ("AL-02", "sent"),
                ("乙县人民医院", "结算清单质控率"): ("AL-04", "ack"),
                ("丙区第2医院", "医保外费用占比"): ("AL-05", "ignored")}
    out = evaluate(RULES, demo_series(), ORGS, existing)
    by = {a["metric"]: a for a in out["alerts"]}
    assert (by["ES35 14天再住院率"]["id"], by["ES35 14天再住院率"]["status"]) == ("AL-02", "sent")
    assert by["结算清单质控率"]["status"] == "ack"
    assert by["医保外费用占比"]["status"] == "unsent"  # unknown status → unsent


def test_evaluate_orders_by_level():
    rules = [R_YUAN, R_LT, R_MOM]
    out = evaluate(rules, demo_series(), ORGS)
    assert [a["level"] for a in out["alerts"]] == ["high", "mid", "low"]


def test_evaluate_earlier_period():
    out = evaluate(RULES, demo_series(), ORGS, period=date(2026, 8, 15))
    assert out["periodKey"] == "2026-08"
    metrics = {a["metric"] for a in out["alerts"]}
    assert "GG19 次均总费用" not in metrics  # 7.7 → 7.6 is no jump
    assert {"BR25 临界区病例占比", "结算清单质控率", "IU29 例均基金差额"} <= metrics
    assert "ES35 14天再住院率" not in metrics  # peers only reported 2026-09
    trend = next(a for a in out["alerts"] if a["metric"] == "IU29 例均基金差额")["trend"]
    assert trend[-1] == 1560 and len(trend) == 11  # 2025-09 has no data


def test_evaluate_unknown_org_and_missing_metric():
    s = series("H999", R_GT.metric, [50])
    out = evaluate([R_GT, R_LT], s, {})
    assert [a["org"] for a in out["alerts"]] == ["H999"]
    assert out["ruleCount"] == 2


def test_evaluate_empty():
    out = evaluate([], [], {})
    assert out["alerts"] == [] and out["months"] == [] and out["periodKey"] is None
    out = evaluate(RULES, [], ORGS)
    assert out["alerts"] == [] and out["ruleCount"] == 6


def test_batch_id_is_stable_and_data_sensitive():
    s = demo_series()
    a = evaluate(RULES, s, ORGS)["batch"]
    assert a == evaluate(RULES, list(reversed(s)), ORGS)["batch"]
    assert a.startswith("202609-")
    s2 = s[:-1] + [Point(s[-1].org_id, s[-1].metric, s[-1].period, 1)]
    assert evaluate(RULES, s2, ORGS)["batch"] != a
    assert batch_id(None, []).startswith("000000-")


def test_month_helpers():
    assert add_months(date(2026, 1, 31), -1) == date(2025, 12, 1)
    assert add_months(date(2025, 12, 5), 1) == date(2026, 1, 1)
    assert MONTHS[0] == date(2025, 10, 1) and MONTHS[-1] == SEP and len(MONTHS) == 12
    assert latest_period([]) is None
