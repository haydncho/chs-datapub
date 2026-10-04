"""全息图（A2 / B1）引擎计算：以设计稿样例数据核对。"""

from fastapi.testclient import TestClient

from dpub_engine.main import app
from dpub_engine.routers import holo

client = TestClient(app)

# 设计稿 DRG 常量：编码、名称、月均病例、例均差额、次均费用
DRG = [
    ['BR25', '脑缺血性疾患,伴并发症', 1420, 1860, 14200], ['ES35', '呼吸系统感染/炎症,伴并发症', 1280, 240, 9800],
    ['RE19', '恶性增生性疾患化学治疗', 1160, -420, 6900], ['EX25', '慢性阻塞性肺病,伴并发症', 960, -180, 11200],
    ['FR45', '心绞痛', 880, -320, 7600], ['GG19', '肛门及肛周手术', 820, 980, 7400], ['OB29', '阴道分娩', 720, -140, 4300],
    ['IU29', '骨病及其他关节病', 690, 1420, 9600], ['KS15', '糖尿病,伴并发症', 640, 120, 8200], ['OF19', '剖宫产', 610, -260, 8900],
    ['FT35', '高血压', 610, -90, 5200], ['GZ15', '其他消化系统诊断', 540, 90, 6100], ['FM19', '经皮心血管操作及支架置入', 520, -2150, 38600],
    ['GU25', '食管炎、胃肠炎', 470, 60, 4800], ['DT19', '中耳炎及上呼吸道感染', 430, -60, 3400], ['LR15', '肾衰竭', 380, 210, 12800],
    ['HS25', '肝硬化', 270, 330, 13600], ['NS15', '女性生殖系统其他疾患', 220, 40, 5600], ['IC29', '髋、膝关节置换', 210, 2680, 62000],
    ['BR11', '颅内出血性疾患,伴严重并发症', 190, 2950, 48000], ['IB39', '脊柱融合手术', 160, -1680, 71000],
    ['JR15', '乳房良性病变', 150, -110, 6800], ['AH29', '气管切开伴呼吸机支持≥96小时', 45, -2600, 168000]]
EFF = {'BR11': 1.18, 'IC29': 1.14, 'AH29': 1.23, 'LR15': 1.12}
ERR = {'BR25': (24.5, 31), 'GG19': (12.0, 8), 'IU29': (3.0, 26), 'ES35': (10.0, 0)}


def groups():
    out = []
    for code, name, cases, diff, cost in DRG:
        ded, up = ERR.get(code, (2.0, 3))
        out.append({"code": code, "name": name, "cases": cases, "avgDiff": diff, "avgCost": cost,
                    "timeIndex": EFF.get(code, 1.05), "auditDeductWan": ded, "upcodeCases": up})
    return out


def test_radius_and_band():
    assert holo.radius(14200) == 10.42
    assert holo.radius(0) == 5
    assert holo.radius_small(14200) == 8.58
    assert [holo.band(t) for t in (1_000_001, 1_000_000, 400_001, 100_001, 100_000, -100_000, -100_001, -500_000, -500_001)] == [
        "deficit-high", "deficit-mid", "deficit-mid", "deficit-low", "balanced", "balanced", "surplus-low", "surplus-low", "surplus-high"]


def test_key_and_rings_rules():
    assert holo.is_key(190, 2950) and not holo.is_key(1280, 240)
    assert holo.is_key(-100, 5500)  # 绝对值判定
    assert holo.eff_flag(1.11) and not holo.eff_flag(1.10)
    assert holo.err_flag(10, 0) and holo.err_flag(0, 20) and not holo.err_flag(9.9, 19)


def test_panorama_month_matches_design():
    r = client.post("/v1/holo/panorama", json={"period": "月", "groups": groups()}).json()
    by = {g["code"]: g for g in r["groups"]}
    assert r["keyCount"] == 6
    assert sorted(c for c, g in by.items() if g["isKey"]) == ["BR11", "BR25", "FM19", "GG19", "IC29", "IU29"]
    # 合计逆差只计逆差病组（FM19 为结余）
    assert r["keyDeficit"] == 5_547_900 and r["keyDeficitWan"] == 554.8
    assert by["BR25"]["band"] == "deficit-high" and by["FM19"]["band"] == "surplus-high"
    assert by["ES35"]["band"] == "deficit-low" and by["GG19"]["band"] == "deficit-mid" and by["KS15"]["band"] == "balanced"
    assert by["EX25"]["band"] == "surplus-low"
    assert by["BR25"]["shortName"] == "脑缺血性疾患" and by["BR25"]["radius"] == 10.42
    assert {c for c, g in by.items() if g["effFlag"]} == set(EFF)
    assert {c for c, g in by.items() if g["errFlag"]} == set(ERR)
    ax = r["axis"]
    assert ax["xMax"] == 1600 and [t["label"] for t in ax["xTicks"]] == ["0", "400", "800", "1,200", "1,600"]
    assert ax["yMax"] == 3000 and [t["label"] for t in ax["yTicks"]] == ["+3,000", "+1,500", "0", "−1,500", "−3,000"]
    assert [b["key"] for b in r["bands"]][0] == "surplus-high" and len(r["bands"]) == 6


def test_panorama_period_scaling_keeps_colors():
    m = client.post("/v1/holo/panorama", json={"period": "月", "groups": groups()}).json()
    y = client.post("/v1/holo/panorama", json={"period": "年", "groups": groups()}).json()
    assert y["multiplier"] == 12
    assert y["groups"][0]["cases"] == 1420 * 12 and y["groups"][0]["totalWan"] == round(1420 * 1860 * 12 / 10000, 1)
    assert [g["band"] for g in y["groups"]] == [g["band"] for g in m["groups"]]
    assert y["keyCount"] == m["keyCount"] and y["keyDeficitWan"] == round(554.79 * 12, 1)
    assert y["axis"]["xMax"] == 1600 * 12 and y["axis"]["xTicks"][1]["label"] == "4,800"
    q = client.post("/v1/holo/panorama", json={"period": "季", "groups": groups()}).json()
    assert q["multiplier"] == 3 and q["keyDeficitWan"] == 1664.4


def test_group_detail():
    body = {"period": "季", "group": groups()[0], "costMix": [30, 8, 19, 15, 28], "benchMix": [26, 5, 21, 18, 30], "peerPct": 81,
            "behaviors": [{"name": "使用高值耗材", "ratePct": 20, "multiplier": 2.4}],
            "gaps": [{"metric": "例均费用", "value": 14200, "bench": 11928, "unit": "元", "kind": "ratio"},
                     {"metric": "平均住院日", "value": 9.4, "bench": 7.1, "unit": "天", "kind": "diff"},
                     {"metric": "药品占比", "value": 30, "bench": 24, "unit": "%", "kind": "pt"}]}
    r = client.post("/v1/holo/group-detail", json=body).json()
    assert r["cases"] == 4260 and r["direction"] == "逆差" and r["isKey"] is True
    assert r["totalWan"] == 792.4 and r["peerLevel"] == "danger"
    assert [g["gap"] for g in r["gaps"]] == ["+19%", "+2.3 天", "+6 pt"]
    assert r["gaps"][0]["value"] == "14,200 元" and r["gaps"][0]["bench"] == "11,928 元"
    assert r["costMix"][0] == {"name": "药品", "mine": 30, "bench": 26}
    assert holo.peer_level(60) == "warning" and holo.peer_level(59) == "primary"


def test_flows_share_and_width():
    flows = [{"region": "省会市", "scope": "省内", "amountWan": 18600}, {"region": "邻市", "scope": "省内", "amountWan": 7062},
             {"region": "外省A市", "scope": "省外", "amountWan": 5893}, {"region": "外省B市", "scope": "省外", "amountWan": 4773},
             {"region": "外省C市", "scope": "省外", "amountWan": 3117}, {"region": "其他地区", "scope": "其他", "amountWan": 9253}]
    r = client.post("/v1/holo/flows", json={"flows": flows}).json()
    assert [f["sharePct"] for f in r["flows"]] == [38.2, 14.5, 12.1, 9.8, 6.4, 19.0]
    assert [f["amountText"] for f in r["flows"]][:2] == ["1.86亿", "7,062万"]
    assert r["totalText"] == "4.87 亿"
    assert r["flows"][0]["strokeWidth"] == 40.1 and r["flows"][4]["strokeWidth"] == 6.7
    tiny = client.post("/v1/holo/flows", json={"flows": [{"region": "a", "scope": "省内", "amountWan": 1}, {"region": "b", "scope": "省外", "amountWan": 999}]}).json()
    assert tiny["flows"][0]["strokeWidth"] == 3.0


def test_publication_counts():
    P = lambda v: {"state": "PUB", "value": v}  # noqa: E731
    NP, NA, INT = {"state": "NP"}, {"state": "NA"}, {"state": "INT"}
    rows = [
        {"name": "例均基金差额", "grp": "钱", "cells": [P(94), P(88), P(71), P(19), P(92), P(100), P(83)]},
        {"name": "次均总费用分位", "grp": "钱", "cells": [P(91), P(84), NP, NP, P(90), P(100), P(80)]},
        {"name": "医保外费用占比", "grp": "钱", "cells": [{"state": "CMT", "value": 6}, P(76), P(62), NA, P(88), P(100), NP]},
        {"name": "CMI值", "grp": "效", "cells": [P(96), P(89), P(70), NA, P(93), P(100), P(85)]},
        {"name": "费用/时间消耗指数", "grp": "效", "cells": [P(90), P(22), P(14), NA, P(86), P(100), P(78)]},
        {"name": "结算清单质控率", "grp": "错", "cells": [{"state": "CMT", "value": 9}, P(82), P(68), P(11), P(95), P(100), NA]},
        {"name": "异地就医基金支出占比", "grp": "区域外", "cells": [P(72), P(66), NA, NA, P(91), P(100), P(88)]},
        {"name": "单病例费用明细", "grp": "仅内部", "cells": [INT] * 7}]
    r = client.post("/v1/holo/publication", json={"rows": rows, "status": {"opinions": 23, "replied": 18}}).json()
    assert r["counts"] == {"np": 3, "low": 4, "cmt": 2}
    assert r["replyPct"] == 78
    assert r["rows"][0]["cells"][3] == {"status": "low", "value": 19, "text": "低查阅 19%"}
    assert r["rows"][0]["cells"][0]["text"] == "已发 94%"
    assert r["rows"][2]["cells"][0]["text"] == "意见 6 条未复"
    assert r["rows"][7]["internal"] is True and r["rows"][0]["internal"] is False
    assert holo.classify(holo.CellIn(state="PUB", value=30)).status == "ok"


def hospital_body(peer_count=6):
    own = [("BR25", 394, 2147), ("ES35", 383, 245), ("IU29", 213, 1300), ("GG19", 216, 1035), ("IC29", 69, 2894),
           ("BR11", 66, 2303), ("KS15", 210, -28), ("FM19", 157, -2287), ("IB39", 17, -2258), ("JR15", 22, -283), ("AH29", 6, -3224)]
    meta = {d[0]: d for d in DRG}
    return {
        "own": [{"code": c, "name": meta[c][1], "cases": n, "avgDiff": df, "avgCost": meta[c][4]} for c, n, df in own],
        "peers": [{"code": d[0], "avgCases": round(d[2] * 0.22, 1), "avgDiff": d[3], "avgCost": d[4]} for d in DRG],
        "peerCount": peer_count,
        "indicators": [{"name": "CMI", "value": "1.12", "unit": "", "pct": 68, "higherWorse": False},
                       {"name": "医保外费用占比", "value": "6.8", "unit": "%", "pct": 72, "higherWorse": True},
                       {"name": "次均费用", "value": "12,860", "unit": "元", "pct": 58, "higherWorse": True}],
        "ledger": {"ledgerWan": 4862.4, "drgPayWan": 4615.7, "cases": 5076, "diffPct": 62},
        "trend": [{"month": m, "avgDiff": v} for m, v in zip(["3月", "4月", "5月", "6月", "7月", "8月"], [212, 305, 198, 402, 455, 486])],
    }


def test_hospital_small_sample_and_peers():
    r = client.post("/v1/holo/hospital", json=hospital_body()).json()
    codes = [o["code"] for o in r["own"]]
    assert "AH29" not in codes and "IB39" not in codes and "JR15" not in codes
    assert r["merged"] == {"count": 3, "cases": 45, "avgDiff": -1421}
    # 灰色背景只含与本院可对照的病组，且不含任何机构字段
    assert {p["code"] for p in r["peers"]} == set(codes)
    assert set(r["peers"][0]) == {"code", "cases", "avgDiff", "radius"}
    by = {o["code"]: o for o in r["own"]}
    assert by["KS15"]["faded"] is True and by["BR25"]["faded"] is False
    assert by["BR25"]["peerDiff"] == 1860 and by["FM19"]["direction"] == "结余"
    assert [t["code"] for t in r["top"]] == ["BR25", "IU29", "GG19", "IC29"]
    assert r["top"][0]["shortName"] == "脑缺血性疾患"
    assert r["axis"]["xMax"] == 500 and r["axis"]["yMax"] == 3000


def test_hospital_indicators_ledger_trend():
    r = client.post("/v1/holo/hospital", json=hospital_body()).json()
    rows = r["indicators"]["rows"]
    assert r["indicators"]["suppressed"] is False
    assert [x["tone"] for x in rows] == ["primary", "warning", "primary"]  # CMI 高为好不标橙
    assert rows[1]["note"] == "高于同级 P70,关注"
    lg = r["ledger"]
    assert lg["deviationWan"] == 246.7 and lg["direction"] == "逆差" and lg["deviationPct"] == -5.1 and lg["avgDiff"] == 486
    assert [t["heightPct"] for t in r["trend"]][-1] == 97.2


def test_hospital_suppresses_percentiles_for_small_peer_group():
    r = client.post("/v1/holo/hospital", json=hospital_body(peer_count=4)).json()
    assert r["indicators"] == {"suppressed": True, "message": "同级机构不足 5 家,不输出同级分位", "rows": []}


def test_validation():
    bad = client.post("/v1/holo/panorama", json={"period": "周", "groups": []})
    assert bad.status_code == 422
