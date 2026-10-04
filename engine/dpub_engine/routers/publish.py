"""发布工作流（A8）：定向范围覆盖。路由前缀 /v1/publish。

定向范围按五个维度圈定受众：等级、县区（多选）、付费批次、医共体、收治病组（单选，「全部 / 不限」不过滤），
各维度之间取交集。覆盖机构数与名单只在引擎计算，服务端与前端不自行计算。
"""

from __future__ import annotations

from fastapi import APIRouter
from pydantic import Field

from ..models import Camel

router = APIRouter(prefix="/v1/publish", tags=["publish"])

#: 单选维度的「不过滤」取值
ANY_BATCH = "全部"
ANY = "不限"


class OrgIn(Camel):
    name: str
    tier: str
    district: str
    batch: str
    alliance: str
    groups: list[str] = Field(default_factory=list)


class Scope(Camel):
    tiers: list[str] = Field(default_factory=list)
    districts: list[str] = Field(default_factory=list)
    batch: str = ANY_BATCH
    alliance: str = ANY
    group: str = ANY


class CoverageRequest(Camel):
    orgs: list[OrgIn]
    scope: Scope
    #: 名单摘要展示前 N 家
    preview_size: int = Field(default=5, ge=1, le=50)


class TierCount(Camel):
    tier: str
    count: int
    total: int


class CoverageResponse(Camel):
    count: int
    total: int
    names: list[str]
    preview: str
    by_tier: list[TierCount]


def _hit(o: OrgIn, s: Scope) -> bool:
    return (
        o.tier in s.tiers
        and o.district in s.districts
        and (s.batch == ANY_BATCH or o.batch == s.batch)
        and (s.alliance == ANY or o.alliance == s.alliance)
        and (s.group == ANY or s.group in o.groups)
    )


def coverage(req: CoverageRequest) -> CoverageResponse:
    hits = [o for o in req.orgs if _hit(o, req.scope)]
    names = [o.name for o in hits]
    n = req.preview_size
    preview = "、".join(names[:n]) + (f" 等 {len(names)} 家" if len(names) > n else "")
    # 等级分布按输入顺序（服务端已按界面顺序排好）
    tiers: list[str] = []
    for o in req.orgs:
        if o.tier not in tiers:
            tiers.append(o.tier)
    by_tier = [
        TierCount(tier=t, count=sum(1 for o in hits if o.tier == t), total=sum(1 for o in req.orgs if o.tier == t))
        for t in tiers
    ]
    return CoverageResponse(count=len(hits), total=len(req.orgs), names=names, preview=preview, by_tier=by_tier)


@router.post("/coverage", response_model=CoverageResponse, response_model_by_alias=True)
def coverage_api(req: CoverageRequest) -> CoverageResponse:
    """实时覆盖机构数与名单。"""
    return coverage(req)
