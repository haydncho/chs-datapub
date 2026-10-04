"""A8 定向范围覆盖：样例机构名单与设计稿原型同一生成规则（52 家）。"""

from fastapi.testclient import TestClient

from dpub_engine.main import app

client = TestClient(app)

TIERS = [("市三级", 6), ("县三级", 4), ("二级甲等", 9), ("二级其他", 11), ("一级", 22)]
DIST = ["市区", "丙区", "甲县", "乙县"]
N3 = ["示例市第一人民医院", "示例市第二人民医院", "示例市第三人民医院", "示例市中医院", "示例市妇幼保健院", "示例市肿瘤医院"]
NC = ["甲县人民医院", "乙县人民医院", "甲县中医院", "乙县中医院"]


def _orgs():
    out = []
    for ti, (tier, n) in enumerate(TIERS):
        for i in range(n):
            dist = ["市区", "丙区"][i % 2] if ti == 0 else ["甲县", "乙县"][i % 2] if ti == 1 else DIST[(i + ti) % 4]
            if ti == 0:
                name = N3[i]
            elif ti == 1:
                name = NC[i]
            elif ti == 3 and i == 0:
                name = "某肛肠专科医院"
            elif ti == 4:
                name = f"{dist}第{i + 1}社区卫生服务中心"
            elif ti == 2:
                name = f"{dist}第{i + 1}医院"
            else:
                name = f"{dist}康复医院{i + 1}院"
            groups = []
            if ti <= 2 or i % 3 == 0:
                groups.append("BR25")
            if (ti == 3 and i < 3) or (ti == 0 and i < 2) or (ti == 2 and i % 4 == 1):
                groups.append("GG19")
            out.append({
                "name": name, "tier": tier, "district": dist,
                "batch": "第二批" if (i + ti) % 3 == 0 else "第一批",
                "alliance": "甲县医共体" if dist == "甲县" else "乙县医共体" if dist == "乙县" else "—",
                "groups": groups,
            })
    return out


ORGS = _orgs()
ALL = {"tiers": [t for t, _ in TIERS], "districts": DIST, "batch": "全部", "alliance": "不限", "group": "不限"}


def cov(**scope):
    r = client.post("/v1/publish/coverage", json={"orgs": ORGS, "scope": {**ALL, **scope}})
    assert r.status_code == 200
    return r.json()


def test_full_scope_covers_all_52():
    r = cov()
    assert r["count"] == 52 and r["total"] == 52
    assert r["preview"] == "示例市第一人民医院、示例市第二人民医院、示例市第三人民医院、示例市中医院、示例市妇幼保健院 等 52 家"
    assert [(x["tier"], x["count"], x["total"]) for x in r["byTier"]] == [
        ("市三级", 6, 6), ("县三级", 4, 4), ("二级甲等", 9, 9), ("二级其他", 11, 11), ("一级", 22, 22)]


def test_tier_and_district_filters():
    assert cov(tiers=["市三级", "县三级", "二级甲等", "二级其他"])["count"] == 30
    r = cov(districts=["甲县"])
    assert r["count"] == 12
    assert all(n.startswith("甲县") for n in r["names"])
    assert cov(tiers=[])["count"] == 0
    assert cov(tiers=[])["preview"] == ""


def test_single_choice_dimensions():
    assert cov(group="BR25")["count"] == 31
    gg = cov(group="GG19")
    assert gg["count"] == 7
    assert "某肛肠专科医院" in gg["names"]
    assert cov(alliance="乙县医共体")["count"] == 12
    assert cov(batch="第二批")["count"] == 17
    # 维度之间取交集
    r = cov(tiers=["县三级"], alliance="甲县医共体")
    assert r["names"] == ["甲县人民医院", "甲县中医院"]
    assert r["preview"] == "甲县人民医院、甲县中医院"


def test_preview_size_and_validation():
    r = client.post("/v1/publish/coverage", json={"orgs": ORGS, "scope": ALL, "previewSize": 2}).json()
    assert r["preview"] == "示例市第一人民医院、示例市第二人民医院 等 52 家"
    assert client.post("/v1/publish/coverage", json={"scope": ALL}).status_code == 422
