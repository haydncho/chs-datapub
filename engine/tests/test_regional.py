"""C1 县区视图 / C2 省级汇总：与设计稿样例数据逐项核对。"""

from fastapi.testclient import TestClient

from dpub_engine.main import app
from dpub_engine.routers.regional import period_key

client = TestClient(app)

# ---------------------------------------------------------------- C1

INST = [
    ("甲县人民医院", "县三级", 2860, 712, 95.4, 78),
    ("甲县中医院", "县三级", 1240, -138, 96.8, 41),
    ("甲县妇幼保健院", "二级甲等", 620, 96, 97.1, 55),
    ("甲县康复医院", "二级其他", 310, -62, 94.2, 36),
    ("甲县第1社区卫生服务中心", "一级", 180, -41, 91.2, 30),
    ("甲县第3社区卫生服务中心", "一级", 146, -24, 92.6, 34),
]
COUNTIES = [
    {"name": "甲县", "avgDiff": 412, "listQcPct": 95.1, "score": 90.1, "self": True},
    {"name": "丙区", "avgDiff": 138, "listQcPct": 92.4, "score": 86.2},
    {"name": "市区", "avgDiff": -82, "listQcPct": 91.6, "score": 92.4},
    {"name": "乙县", "avgDiff": 96, "listQcPct": 93.8, "score": 88.7},
]
# 名称、取值、单位、方向、关注线、预警线 → 设计稿状态
MONITORS = [
    ("县域内住院率", 88.6, "%", "high", 85, 80, "ok"),
    ("县域内基金支出占比", 71.2, "%", "high", 75, 65, "warn"),
    ("基层就诊率", 63.4, "%", "high", 60, 50, "ok"),
    ("上转率", 4.1, "%", "low", 5, 8, "ok"),
    ("下转率", 2.2, "%", "high", 3, 1.5, "warn"),
    ("医共体基金结余率", 3.8, "%", "high", 2, 0, "ok"),
    ("次均住院费用", 7820, "元", "low", 8500, 9500, "ok"),
    ("例均基金差额", 412, "元", "low", 200, 400, "bad"),
    ("慢病规范管理率", 76.0, "%", "high", 70, 60, "ok"),
    ("县外住院人次占比", 11.4, "%", "low", 10, 15, "warn"),
    ("家庭医生签约服务费到位率", 92.0, "%", "high", 90, 80, "ok"),
    ("药品耗材联合采购率", 97.5, "%", "high", 95, 90, "ok"),
    ("结算清单质控率", 95.1, "%", "high", 95, 90, "ok"),
    ("住院患者满意度", 91.3, "%", "high", 90, 85, "ok"),
]


def county_body():
    return {
        "institutions": [{"name": n, "level": l, "cases": c, "avgDiff": d, "listQcPct": q, "costPctl": p} for n, l, c, d, q, p in INST],
        "counties": COUNTIES,
        "indicators": [{"name": n, "value": v, "unit": u, "signed": n == "例均基金差额", "direction": dr, "warn": w, "alarm": a}
                       for n, v, u, dr, w, a, _ in MONITORS],
    }


def test_county_rank_matches_design():
    r = client.post("/v1/regional/county", json=county_body()).json()
    assert [(c["name"], c["rank"]) for c in r["counties"]] == [("市区", 1), ("甲县", 2), ("乙县", 3), ("丙区", 4)]
    assert [c["avgDiffText"] for c in r["counties"]] == ["−82 元", "+412 元", "+96 元", "+138 元"]
    own = [c for c in r["counties"] if c["self"]]
    assert len(own) == 1 and own[0]["name"] == "甲县"


def test_county_rank_ties_share_rank():
    body = county_body()
    body["counties"] = [{"name": "A", "avgDiff": 0, "listQcPct": 90, "score": 80},
                        {"name": "B", "avgDiff": 0, "listQcPct": 90, "score": 80},
                        {"name": "C", "avgDiff": 0, "listQcPct": 90, "score": 70}]
    r = client.post("/v1/regional/county", json=body).json()
    assert [c["rank"] for c in r["counties"]] == [1, 1, 3]


def test_county_monitor_status_and_display():
    r = client.post("/v1/regional/county", json=county_body()).json()
    assert [m["status"] for m in r["indicators"]] == [m[-1] for m in MONITORS]
    assert r["statusCounts"] == {"ok": 10, "warn": 3, "bad": 1}
    disp = {m["name"]: m["display"] for m in r["indicators"]}
    assert disp["次均住院费用"] == "7,820 元"
    assert disp["例均基金差额"] == "+412 元"
    assert disp["慢病规范管理率"] == "76.0%"
    rule = {m["name"]: m["rule"] for m in r["indicators"]}["县外住院人次占比"]
    assert rule == "关注线 > 10.0% · 预警线 > 15.0%"


def test_county_qc_low_flags():
    r = client.post("/v1/regional/county", json=county_body()).json()
    low = [i["name"] for i in r["institutions"] if i["qcLow"]]
    assert low == ["甲县康复医院", "甲县第1社区卫生服务中心", "甲县第3社区卫生服务中心"]
    assert [i["avgDiffText"] for i in r["institutions"][:2]] == ["+712 元", "−138 元"]


# ---------------------------------------------------------------- C2

REG = [
    # 名称, 期次, 最近发布日, 下期截止, 签收, 上期签收, 查阅, 答复, 结余率, 收入(亿), 覆盖率, 例均差额
    ("示例市", "2026年8月 月告知", "2026-09-12", "2026-10-15", 97, 96, 86, 78, 4.2, 43.7, 98.6, -38),
    ("统筹区02", "2026年8月 月告知", "2026-09-10", "2026-10-15", 95, 94, 81, 82, 5.1, 18.6, 97.2, -112),
    ("统筹区03", "2026年8月 月告知", "2026-09-14", "2026-10-15", 92, 91, 74, 66, 2.8, 24.6, 96.5, 64),
    ("统筹区04", "2026年8月 月告知", "2026-09-09", "2026-10-15", 99, 98, 90, 88, 6.3, 12.8, 99.1, -205),
    ("统筹区05", "2026年第二季度 季公布", "2026-07-20", "2026-10-20", 88, 86, 69, 59, 1.9, 30.5, 94.8, 138),
    ("统筹区06", "2026年7月 月告知", "2026-08-16", "2026-09-18", 81, 79, 52, 44, -0.6, 12.4, 92.3, 322),
    ("统筹区07", "2026年8月 月告知", "2026-09-13", "2026-10-15", 94, 93, 79, 71, 3.5, 19.3, 97.8, -21),
    ("统筹区08", "2026年8月 月告知", "2026-09-11", "2026-10-15", 96, 92, 84, 80, 4.8, 14.1, 98.0, -76),
]


def prov(sort="signPct", dir="desc"):
    regions = [{"name": n, "self": n == "示例市", "lastPeriod": p, "lastDate": ld, "nextDue": nd, "signPct": s, "prevSignPct": ps,
                "readPct": rd, "replyPct": rp, "balancePct": b, "fundIncome": inc, "coveragePct": cv, "avgDiff": d}
               for n, p, ld, nd, s, ps, rd, rp, b, inc, cv, d in REG]
    res = client.post("/v1/regional/province", json={"asOf": "2026-09-30", "regions": regions, "sort": sort, "dir": dir})
    assert res.status_code == 200, res.text
    return res.json()


def test_province_kpis_match_design():
    k = prov()["kpis"]
    assert (k["onTime"], k["total"]) == (7, 8)
    assert k["overdue"] == [{"name": "统筹区06", "days": 12}]
    assert k["avgSignPct"] == 92.8          # 92.75 四舍五入
    assert k["signDeltaPt"] == 1.6
    assert k["avgReplyPct"] == 71.0
    assert k["lowReplyCount"] == 2
    assert k["balancePct"] == 3.5           # 按基金收入加权
    assert k["deficitCount"] == 1


def test_province_overdue_row():
    rows = {r["name"]: r for r in prov()["rows"]}
    late = rows["统筹区06"]
    assert late["overdue"] is True and late["dateLabel"] == "逾期 12 天" and late["deficit"] is True and late["readLow"] is True
    assert rows["示例市"]["dateLabel"] == "09-12" and rows["示例市"]["overdue"] is False and rows["示例市"]["self"] is True
    assert rows["统筹区05"]["overdue"] is False          # 季公布，下期 10-20 才到期
    assert rows["统筹区06"]["avgDiffText"] == "+322 元" and rows["示例市"]["avgDiffText"] == "−38 元"


def test_province_default_sort_sign_desc():
    names = [r["name"] for r in prov()["rows"]]
    assert names == ["统筹区04", "示例市", "统筹区08", "统筹区02", "统筹区07", "统筹区03", "统筹区05", "统筹区06"]


def test_province_sort_toggle_and_keys():
    asc = [r["name"] for r in prov("signPct", "asc")["rows"]]
    assert asc[0] == "统筹区06" and asc[-1] == "统筹区04"
    diff = [r["avgDiff"] for r in prov("avgDiff", "desc")["rows"]]
    assert diff == sorted(diff, reverse=True)
    # 期次按时间序：季公布(6 月) < 7 月 < 8 月
    per = [r["name"] for r in prov("period", "asc")["rows"]]
    assert per[:2] == ["统筹区05", "统筹区06"]
    # 发布日期按实际日期，不按「逾期 N 天」文字
    dates = [r["name"] for r in prov("date", "desc")["rows"]]
    assert dates[0] == "统筹区03" and dates[-1] == "统筹区05"


def test_province_desc_ties_keep_name_order():
    regions = [{"name": n, "lastPeriod": "2026年8月 月告知", "lastDate": "2026-09-10", "nextDue": "2026-10-15", "signPct": 90,
                "prevSignPct": 90, "readPct": 80, "replyPct": 80, "balancePct": 1, "fundIncome": 1, "coveragePct": 90, "avgDiff": 0}
               for n in ["乙", "甲", "丙"]]
    r = client.post("/v1/regional/province", json={"asOf": "2026-09-30", "regions": regions, "sort": "signPct", "dir": "desc"}).json()
    assert [x["name"] for x in r["rows"]] == sorted(["乙", "甲", "丙"])


def test_province_rejects_unknown_sort():
    r = client.post("/v1/regional/province", json={"asOf": "2026-09-30", "regions": [], "sort": "orgName"})
    assert r.status_code == 422


def test_period_key():
    assert period_key("2026年8月 月告知") == (2026, 8)
    assert period_key("2026年第二季度 季公布") == (2026, 6)
    assert period_key("2025年度 年报") == (2025, 12)
    assert period_key("未知") == (0, 0)
