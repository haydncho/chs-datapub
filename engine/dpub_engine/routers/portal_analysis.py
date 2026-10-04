"""机构门户分析（B2 / B3 / B7）。路由前缀 /v1/portal-analysis。

- B3 对标：同级分位（P0/P25/P50/P75/P100）、本院分位位置、同级排名、匿名编号；
  同级机构 < 5 家时不输出分位（小样本抑制）。
- B2 病组：小样本病组（病例 < 30）并入「其他」并给出合并后的例均差额；
  关键行为偏差、标杆差距与「关注」判定。

引擎只接收机构的不透明编号（id）与数值，不接收机构名称；名称是否下发由 server 按对标档位裁剪。
"""

from __future__ import annotations

import string

from fastapi import APIRouter, HTTPException
from pydantic import Field

from ..models import Camel

router = APIRouter(prefix="/v1/portal-analysis", tags=["portal-analysis"])
OPTS = {"response_model_by_alias": True, "response_model_exclude_none": True}

#: 小样本抑制阈值（三条贯穿规则）：同级机构 < 5 家不输出同级分位；机构端病组病例 < 30 显示「已并入其他」
MIN_PEERS = 5
MIN_CASES = 30
#: 分位条「关注」阈值：本院 ≥ P70 且该指标「高为差」时标橙
CONCERN_PCT = 70


def _round(x: float, nd: int = 0) -> float:
    """四舍五入（远离零），避免银行家舍入导致的展示偏差。"""
    q = 10**nd
    v = int(abs(x) * q + 0.5 + 1e-9) / q
    return -v if x < 0 else v


def quantile(sorted_vals: list[float], p: float) -> float:
    """线性插值分位数（与 numpy 默认 linear 一致）：位置 = p × (n − 1)。"""
    n = len(sorted_vals)
    if n == 1:
        return sorted_vals[0]
    pos = p * (n - 1)
    lo = int(pos)
    hi = min(lo + 1, n - 1)
    return sorted_vals[lo] + (sorted_vals[hi] - sorted_vals[lo]) * (pos - lo)


def percentile_rank(sorted_vals: list[float], v: float) -> float:
    """数值在同级中的分位位置（0–100），为 quantile 的反函数；相同数值取平均位置。"""
    n = len(sorted_vals)
    if n == 1:
        return 50.0
    idx = [i for i, x in enumerate(sorted_vals) if x == v]
    if idx:
        pos = sum(idx) / len(idx)
    elif v <= sorted_vals[0]:
        pos = 0.0
    elif v >= sorted_vals[-1]:
        pos = n - 1.0
    else:
        hi = next(i for i, x in enumerate(sorted_vals) if x > v)
        lo = hi - 1
        pos = lo + (v - sorted_vals[lo]) / (sorted_vals[hi] - sorted_vals[lo])
    return pos / (n - 1) * 100


# ---------------------------------------------------------------- B3 对标


class BenchItem(Camel):
    id: str
    value: float


class BenchRequest(Camel):
    items: list[BenchItem] = Field(min_length=1)
    own_id: str
    #: 高为好（如 CMI、质控率）；否则高为差（如费用、再住院率）
    higher_is_better: bool = False
    min_peers: int = MIN_PEERS
    #: 匿名编号前缀，如「三级医院」
    anon_prefix: str = "三级医院"


class Quantiles(Camel):
    p0: float
    p25: float
    p50: float
    p75: float
    p100: float


class BenchRow(Camel):
    id: str
    value: float
    #: 同级排名（1 = 表现最好，按 higher_is_better 方向；数值相同名次相同）
    rank: int
    own: bool
    #: 匿名编号（本院为「本院」）：按数值降序依次编号，不与机构身份绑定，跨指标、跨期不可关联
    anon_label: str


class BenchResponse(Camel):
    n: int
    suppressed: bool
    message: str | None = None
    own_value: float
    quantiles: Quantiles | None = None
    #: 本院分位位置（0–100，取整），同时是分位条上本院标记的横向位置
    own_pct: int | None = None
    #: 本院 ≥ P70 且高为差 → 关注（标橙）
    concern: bool = False
    own_rank: int
    #: 按数值降序（匿名编号横条）
    rows: list[BenchRow]
    #: 按排名升序（具名排行）
    ranking: list[BenchRow]


def _ranks(items: list[BenchItem], higher_is_better: bool) -> dict[str, int]:
    vals = sorted((it.value for it in items), reverse=higher_is_better)
    return {it.id: vals.index(it.value) + 1 for it in items}


@router.post("/benchmark", response_model=BenchResponse, **OPTS)
def benchmark(req: BenchRequest) -> BenchResponse:
    own = next((it for it in req.items if it.id == req.own_id), None)
    if own is None:
        raise HTTPException(status_code=422, detail="请求参数不合法：ownId 不在同级机构列表中")
    n = len(req.items)
    ranks = _ranks(req.items, req.higher_is_better)
    letters = iter(string.ascii_uppercase)
    rows = [
        BenchRow(
            id=it.id,
            value=it.value,
            rank=ranks[it.id],
            own=it.id == req.own_id,
            anon_label="本院" if it.id == req.own_id else f"{req.anon_prefix}{next(letters)}",
        )
        for it in sorted(req.items, key=lambda it: (-it.value, it.id))
    ]
    ranking = sorted(rows, key=lambda r: (r.rank, not r.own, r.id))
    base = dict(n=n, own_value=own.value, own_rank=ranks[own.id], rows=rows, ranking=ranking)
    if n < req.min_peers:
        return BenchResponse(suppressed=True, message=f"同级机构不足 {req.min_peers} 家,不输出同级分位", **base)
    vals = sorted(it.value for it in req.items)
    q = Quantiles(**{f"p{p}": quantile(vals, p / 100) for p in (0, 25, 50, 75, 100)})
    pct = int(_round(percentile_rank(vals, own.value)))
    return BenchResponse(
        suppressed=False,
        quantiles=q,
        own_pct=pct,
        concern=(not req.higher_is_better) and pct >= CONCERN_PCT,
        **base,
    )


# ---------------------------------------------------------------- B2 病组


class GroupIn(Camel):
    code: str
    cases: int = Field(ge=0)
    avg_diff: float


class SmallSampleRequest(Camel):
    groups: list[GroupIn]
    min_cases: int = MIN_CASES


class Merged(Camel):
    codes: list[str]
    cases: int
    #: 并入「其他」的病组按病例数加权的例均基金差额（元，取整）
    avg_diff: int


class SmallSampleResponse(Camel):
    kept: list[str]
    merged: Merged


@router.post("/small-sample", response_model=SmallSampleResponse, **OPTS)
def small_sample(req: SmallSampleRequest) -> SmallSampleResponse:
    """病例 < min_cases 的病组不单独展示，并入「其他」。"""
    kept = [g.code for g in req.groups if g.cases >= req.min_cases]
    small = [g for g in req.groups if g.cases < req.min_cases]
    cases = sum(g.cases for g in small)
    diff = sum(g.cases * g.avg_diff for g in small) / cases if cases else 0
    return SmallSampleResponse(kept=kept, merged=Merged(codes=[g.code for g in small], cases=cases, avg_diff=int(_round(diff))))


class BehaviorIn(Camel):
    name: str
    own_pct: float
    peer_median_pct: float
    #: 发生率越高越差（如重复检查）；False 表示越低越差（如康复治疗介入）
    higher_is_worse: bool = True


class GapIn(Camel):
    name: str
    own: float
    bench: float
    higher_is_worse: bool = True
    #: 差距保留的小数位
    decimals: int = 0


class GroupRequest(Camel):
    cost_pct: int = Field(ge=0, le=100)
    behaviors: list[BehaviorIn]
    gaps: list[GapIn]


class BehaviorOut(Camel):
    name: str
    own_pct: float
    peer_median_pct: float
    deviation: float
    #: 朝不利方向偏离同级中位 → 标橙
    concern: bool


class GapOut(Camel):
    name: str
    own: float
    bench: float
    gap: float
    #: 本院比标杆组差 → 标红
    worse: bool


class GroupResponse(Camel):
    cost_pct_concern: bool
    behaviors: list[BehaviorOut]
    gaps: list[GapOut]


@router.post("/group", response_model=GroupResponse, **OPTS)
def group(req: GroupRequest) -> GroupResponse:
    behaviors = []
    for b in req.behaviors:
        dev = _round(b.own_pct - b.peer_median_pct, 1)
        behaviors.append(
            BehaviorOut(
                name=b.name,
                own_pct=b.own_pct,
                peer_median_pct=b.peer_median_pct,
                deviation=dev,
                concern=dev > 0 if b.higher_is_worse else dev < 0,
            )
        )
    gaps = []
    for g in req.gaps:
        gap = _round(g.own - g.bench, g.decimals)
        gaps.append(GapOut(name=g.name, own=g.own, bench=g.bench, gap=gap, worse=gap > 0 if g.higher_is_worse else gap < 0))
    # 次均费用为「高为差」指标：≥ P70 标橙（与分位条规则一致）
    return GroupResponse(cost_pct_concern=req.cost_pct >= CONCERN_PCT, behaviors=behaviors, gaps=gaps)
