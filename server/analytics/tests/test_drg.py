from app.drg import KEY_FEW, MINOR, STABLE, WATCH, Group, panorama, quadrant, recommend

G = [
    Group("BR25", "脑缺血性疾患,伴并发症", 1420, 1860, 14200),
    Group("ES35", "呼吸系统感染/炎症,伴并发症", 1280, 240, 9800),
    Group("FM19", "经皮心血管操作及支架置入", 520, -2150, 38600),
    Group("IC29", "髋、膝关节置换", 210, 2680, 62000),
    Group("RE19", "恶性增生性疾患化学治疗", 1160, -420, 6900),
]


def test_quadrants():
    assert quadrant(G[0], 1000) == KEY_FEW
    assert quadrant(G[3], 1000) == WATCH
    assert quadrant(G[4], 1000) == STABLE
    assert quadrant(G[2], 1000) == MINOR


def test_panorama_totals_and_order():
    p = panorama(G)
    assert p["medianCases"] == 1160
    assert p["deficitTotal"] == 1420 * 1860 + 1280 * 240 + 210 * 2680
    assert p["surplusTotal"] == 520 * -2150 + 1160 * -420
    assert p["groups"][0]["code"] == "BR25"  # largest |total diff| first
    assert p["groups"][0]["alert"] is True


def test_recommend_candidates_ranked():
    r = recommend(G)
    assert [x["code"] for x in r][0] == "BR25"
    # deficits + the large surplus group FM19 (−111.8 万); RE19 (−48.7 万) stays out
    assert {x["code"] for x in r} == {"BR25", "ES35", "IC29", "FM19"}
    assert all(0 <= x["score"] <= 100 for x in r)
    assert [x["score"] for x in r] == sorted((x["score"] for x in r), reverse=True)


def test_recommend_a6_topic_shape():
    r = {x["code"]: x for x in recommend(G, batch="202609-abcdef")}
    br = r["BR25"]
    assert br["id"] == "T-BR25" and br["kind"] == "病种专题" and br["title"] == "BR25 脑缺血性疾患,伴并发症"
    assert br["dims"] == [br["scores"][k] for k in ("impact", "deviation", "actionable", "ready")]
    assert br["dims"][0] == 100 and br["dims"][2] == 100  # largest impact, largest volume
    assert br["status"] == "open" and br["source"] == "差额排行" and br["batch"] == "202609-abcdef"
    assert br["why"].startswith("逆差总额全市最大(264.1 万)")
    assert br["facts"] == [{"k": "逆差总额", "v": "264.1 万"}, {"k": "例均差额", "v": "+1,860"},
                           {"k": "病例数", "v": "1,420"}, {"k": "差额占次均", "v": "13.1%"}]
    assert "专家组" in br["audiences"]
    assert br["method"]["inputs"]["totalDiff"] == 1420 * 1860
    assert br["method"]["weights"] == {"impact": 0.4, "deviation": 0.25, "actionable": 0.2, "ready": 0.15}
    fm = r["FM19"]
    assert fm["why"] == "结余 −2,150 元/例,疑似低码高编反向"
    assert fm["facts"][0] == {"k": "结余总额", "v": "111.8 万"} and fm["audiences"] == ["市三级"]
    assert r["IC29"]["why"] == "例均逆差 +2,680 元,占次均费用 4.3%"
    assert "batch" not in recommend(G)[0]


def test_recommend_score_is_weighted_sum():
    for x in recommend(G):
        s = x["scores"]
        assert x["score"] == round(0.4 * s["impact"] + 0.25 * s["deviation"] + 0.2 * s["actionable"] + 0.15 * s["ready"])


def test_recommend_top_and_zero_cost():
    assert len(recommend(G, top=2)) == 2
    assert recommend([Group("X1", "x", 10, 100, 0)]) == []


def test_recommend_quality_gate():
    r = recommend(G, quality={"IC29": 70})
    ic = next(x for x in r if x["code"] == "IC29")
    assert ic["publishable"] is False


def test_empty():
    assert panorama([])["groups"] == []
    assert recommend([]) == []
