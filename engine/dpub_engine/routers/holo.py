"""全息图（A2 / B1）：气泡全景、关键少数病组、图层外环、区域外流向、公开状态矩阵、本院全息。路由前缀 /v1/holo。

计算口径（与设计交接说明一致）：
- 气泡半径 = 5 + √次均费用 / 22（B1 本院图较小：4 + √次均费用 / 26）。
- 差额总额色阶（按月均口径判定，切换季 / 年口径时颜色不跳变）：逆差 > 100 万、40–100 万、10–40 万、±10 万、结余 10–50 万、结余 > 50 万。
- 关键少数病组：|月均病例 × 例均差额| ≥ 55 万；合计逆差 = 关键少数中逆差病组的差额总额（按所选口径）。
- “效”图层外环：时间消耗指数 > 1.10；“错”图层外环：本期审核扣款 ≥ 10 万元或疑似高套 ≥ 20 例。
- 月 / 季 / 年口径换算：病例数、差额总额 × 1 / 3 / 12；例均值不变。
- 小样本抑制：本院病组病例 < 30 并入“其他”；同级机构 < 5 家不输出同级分位。
"""

from __future__ import annotations

import math
from typing import Literal

from fastapi import APIRouter
from pydantic import Field

from ..models import Camel

router = APIRouter(prefix="/v1/holo", tags=["holo"])

Period = Literal["月", "季", "年"]
MULTIPLIER: dict[str, int] = {"月": 1, "季": 3, "年": 12}

KEY_THRESHOLD_YUAN = 550_000
EFF_TIME_INDEX = 1.10
ERR_DEDUCT_WAN = 10.0
ERR_UPCODE_CASES = 20
LOW_READ_PCT = 30
MIN_CASES = 30
MIN_PEERS = 5
FADE_DIFF_YUAN = 150

Band = Literal["deficit-high", "deficit-mid", "deficit-low", "balanced", "surplus-low", "surplus-high"]
BANDS: list[tuple[str, str]] = [
    ("surplus-high", "结余>50万"),
    ("surplus-low", "10–50万"),
    ("balanced", "±10万"),
    ("deficit-low", "逆差10–40万"),
    ("deficit-mid", "40–100万"),
    ("deficit-high", "逆差>100万"),
]


# ---------------------------------------------------------------- 纯函数（便于单测）


def radius(avg_cost: float) -> float:
    """A2 气泡半径（px）：5 + √次均费用 / 22。"""
    return round(5 + math.sqrt(max(avg_cost, 0)) / 22, 2)


def radius_small(avg_cost: float) -> float:
    """B1 本院图气泡半径（px）：4 + √次均费用 / 26。"""
    return round(4 + math.sqrt(max(avg_cost, 0)) / 26, 2)


def band(total_yuan: float) -> Band:
    """差额总额色阶分档（元，正为逆差）。"""
    t = total_yuan
    if t > 1_000_000:
        return "deficit-high"
    if t > 400_000:
        return "deficit-mid"
    if t > 100_000:
        return "deficit-low"
    if t >= -100_000:
        return "balanced"
    if t >= -500_000:
        return "surplus-low"
    return "surplus-high"


def is_key(cases: float, avg_diff: float, threshold: float = KEY_THRESHOLD_YUAN) -> bool:
    """关键少数病组：|病例 × 例均差额| ≥ 阈值（月均口径）。"""
    return abs(cases * avg_diff) >= threshold


def eff_flag(time_index: float) -> bool:
    return time_index > EFF_TIME_INDEX


def err_flag(audit_deduct_wan: float, upcode_cases: int) -> bool:
    return audit_deduct_wan >= ERR_DEDUCT_WAN or upcode_cases >= ERR_UPCODE_CASES


def wan(yuan: float) -> float:
    """元 → 万元（1 位小数）。"""
    return round(yuan / 10_000, 1)


def short_name(name: str) -> str:
    """病组简称：取首个逗号前的部分（“脑缺血性疾患,伴并发症” → “脑缺血性疾患”）。"""
    return name.replace("，", ",").split(",")[0]


def nice_step(raw: float) -> float:
    if raw <= 0:
        return 1
    mag = 10 ** math.floor(math.log10(raw))
    for m in (1, 2, 2.5, 4, 5, 10):
        if m * mag >= raw:
            return m * mag
    return 10 * mag


class Tick(Camel):
    value: float
    label: str


class Axis(Camel):
    x_max: float
    x_ticks: list[Tick]
    y_max: float
    y_ticks: list[Tick]


def fmt_int(v: float) -> str:
    return f"{round(v):,}"


def signed(v: float) -> str:
    """带符号千分位整数：+1,860 / −2,150 / 0。"""
    r = round(v)
    if r == 0:
        return "0"
    return ("+" if r > 0 else "−") + f"{abs(r):,}"


def axis(max_cases: float, max_abs_diff: float, mult: int = 1, y_step: int = 1500) -> Axis:
    """横轴（病例数）取约 5 段的整齐刻度并按口径放大；纵轴以 1,500 元为步长、关于收支平衡线对称。"""
    step = nice_step(max(max_cases, 1) * 1.1 / 5)
    x_max = math.ceil(max(max_cases, 1) * 1.1 / step) * step
    y_max = max(1, math.ceil(max_abs_diff / y_step)) * y_step
    xs = [Tick(value=i * step * mult, label=fmt_int(i * step * mult)) for i in range(int(round(x_max / step)) + 1)]
    n = int(y_max / y_step)
    ys = [Tick(value=k * y_step, label=signed(k * y_step)) for k in range(n, -n - 1, -1)]
    return Axis(x_max=x_max * mult, x_ticks=xs, y_max=y_max, y_ticks=ys)


# ---------------------------------------------------------------- A2 气泡全景


class GroupIn(Camel):
    code: str
    name: str
    cases: int = Field(ge=0, description="月均病例数")
    avg_diff: float = Field(description="例均基金差额（元），正为逆差")
    avg_cost: float = Field(ge=0)
    time_index: float = 1.0
    audit_deduct_wan: float = 0
    upcode_cases: int = 0


class PanoramaRequest(Camel):
    period: Period = "月"
    groups: list[GroupIn]
    key_threshold_yuan: float = KEY_THRESHOLD_YUAN


class Bubble(Camel):
    code: str
    name: str
    short_name: str
    cases: int
    avg_diff: float
    avg_cost: float
    total_diff: float
    total_wan: float
    radius: float
    band: Band
    is_key: bool
    eff_flag: bool
    err_flag: bool


class BandLegend(Camel):
    key: Band
    label: str


class PanoramaResponse(Camel):
    period: Period
    multiplier: int
    groups: list[Bubble]
    key_count: int
    key_deficit: float
    key_deficit_wan: float
    axis: Axis
    bands: list[BandLegend]


@router.post("/panorama", response_model=PanoramaResponse, response_model_by_alias=True)
def panorama(req: PanoramaRequest) -> PanoramaResponse:
    """病组气泡全景：半径、色阶、关键少数、两类图层外环与口径换算。"""
    mult = MULTIPLIER[req.period]
    out: list[Bubble] = []
    for g in req.groups:
        monthly_total = g.cases * g.avg_diff
        out.append(Bubble(
            code=g.code, name=g.name, short_name=short_name(g.name), cases=g.cases * mult, avg_diff=g.avg_diff,
            avg_cost=g.avg_cost, total_diff=monthly_total * mult, total_wan=wan(monthly_total * mult),
            radius=radius(g.avg_cost), band=band(monthly_total), is_key=is_key(g.cases, g.avg_diff, req.key_threshold_yuan),
            eff_flag=eff_flag(g.time_index), err_flag=err_flag(g.audit_deduct_wan, g.upcode_cases)))
    keys = [b for b in out if b.is_key]
    deficit = sum(b.total_diff for b in keys if b.avg_diff > 0)
    ax = axis(max((g.cases for g in req.groups), default=1), max((abs(g.avg_diff) for g in req.groups), default=1), mult)
    return PanoramaResponse(period=req.period, multiplier=mult, groups=out, key_count=len(keys), key_deficit=deficit,
                            key_deficit_wan=wan(deficit), axis=ax, bands=[BandLegend(key=k, label=l) for k, l in BANDS])


# ---------------------------------------------------------------- A2 选中病组详情


class BehaviorIn(Camel):
    name: str
    rate_pct: float
    multiplier: float


class GapIn(Camel):
    metric: str
    value: float
    bench: float
    unit: str
    kind: Literal["ratio", "diff", "pt"]


COST_CATEGORIES = ["药品", "耗材", "检查检验", "治疗/手术", "护理及其他"]


class DetailRequest(Camel):
    period: Period = "月"
    group: GroupIn
    cost_mix: list[float] = Field(min_length=5, max_length=5)
    bench_mix: list[float] = Field(min_length=5, max_length=5)
    peer_pct: int = Field(ge=0, le=100)
    behaviors: list[BehaviorIn] = []
    gaps: list[GapIn] = []
    key_threshold_yuan: float = KEY_THRESHOLD_YUAN


class MixRow(Camel):
    name: str
    mine: float
    bench: float


class GapOut(Camel):
    metric: str
    value: str
    bench: str
    gap: str
    worse: bool


class DetailResponse(Camel):
    cases: int
    avg_diff: float
    avg_cost: float
    total_diff: float
    total_wan: float
    direction: Literal["逆差", "结余"]
    is_key: bool
    peer_pct: int
    peer_level: Literal["danger", "warning", "primary"]
    cost_mix: list[MixRow]
    behaviors: list[BehaviorIn]
    gaps: list[GapOut]


def _num(v: float, unit: str) -> str:
    if unit == "元":
        return f"{fmt_int(v)} 元"
    if unit == "%":
        return f"{v:g}%"
    return f"{v:.1f} {unit}"


def gap_text(g: GapIn) -> GapOut:
    """标杆差距：ratio = 相对标杆 ±x%；diff = 绝对差（带单位）；pt = 百分点差。"""
    d = g.value - g.bench
    if g.kind == "ratio":
        pct = round(d / g.bench * 100) if g.bench else 0
        gap = f"{'+' if pct >= 0 else '−'}{abs(pct)}%"
    elif g.kind == "diff":
        gap = f"{'+' if d >= 0 else '−'}{abs(d):.1f} {g.unit}"
    else:
        gap = f"{'+' if d >= 0 else '−'}{abs(d):g} pt"
    return GapOut(metric=g.metric, value=_num(g.value, g.unit), bench=_num(g.bench, g.unit), gap=gap, worse=d > 0)


def peer_level(pct: int) -> Literal["danger", "warning", "primary"]:
    """同级分位标记色：≥ P75 红、≥ P60 橙，否则蓝（次均费用高为差）。"""
    return "danger" if pct >= 75 else "warning" if pct >= 60 else "primary"


@router.post("/group-detail", response_model=DetailResponse, response_model_by_alias=True)
def group_detail(req: DetailRequest) -> DetailResponse:
    mult = MULTIPLIER[req.period]
    g = req.group
    total = g.cases * g.avg_diff * mult
    return DetailResponse(
        cases=g.cases * mult, avg_diff=g.avg_diff, avg_cost=g.avg_cost, total_diff=total, total_wan=wan(abs(total)),
        direction="逆差" if g.avg_diff > 0 else "结余", is_key=is_key(g.cases, g.avg_diff, req.key_threshold_yuan),
        peer_pct=req.peer_pct, peer_level=peer_level(req.peer_pct),
        cost_mix=[MixRow(name=n, mine=m, bench=b) for n, m, b in zip(COST_CATEGORIES, req.cost_mix, req.bench_mix)],
        behaviors=req.behaviors, gaps=[gap_text(x) for x in req.gaps])


# ---------------------------------------------------------------- A2 区域外流向


class FlowIn(Camel):
    region: str
    scope: Literal["省内", "省外", "其他"]
    amount_wan: float = Field(ge=0)


class FlowRequest(Camel):
    flows: list[FlowIn]


class FlowOut(Camel):
    region: str
    scope: Literal["省内", "省外", "其他"]
    amount_wan: float
    amount_text: str
    share_pct: float
    stroke_width: float


class FlowResponse(Camel):
    total_wan: float
    total_text: str
    flows: list[FlowOut]


def amount_text(amount_wan: float) -> str:
    """≥ 1 亿显示“x.xx亿”，否则“x,xxx万”。"""
    return f"{amount_wan / 10_000:.2f}亿" if amount_wan >= 10_000 else f"{round(amount_wan):,}万"


@router.post("/flows", response_model=FlowResponse, response_model_by_alias=True)
def flows(req: FlowRequest) -> FlowResponse:
    """异地就医流向：占比 = 地区基金支出 / 合计；线宽 ∝ 占比（最细 3px）。"""
    total = sum(f.amount_wan for f in req.flows)
    out = []
    for f in req.flows:
        share = round(f.amount_wan / total * 100, 1) if total else 0.0
        out.append(FlowOut(region=f.region, scope=f.scope, amount_wan=f.amount_wan, amount_text=amount_text(f.amount_wan),
                           share_pct=share, stroke_width=round(max(3.0, share * 1.05), 1)))
    return FlowResponse(total_wan=round(total, 1), total_text=f"{total / 10_000:.2f} 亿", flows=out)


# ---------------------------------------------------------------- A2 公开状态矩阵


class CellIn(Camel):
    state: Literal["PUB", "NP", "CMT", "INT", "NA"]
    value: int | None = None


class PubRowIn(Camel):
    name: str
    grp: str
    cells: list[CellIn]


class PubStatusIn(Camel):
    opinions: int = Field(ge=0)
    replied: int = Field(ge=0)


class PublicationRequest(Camel):
    rows: list[PubRowIn]
    status: PubStatusIn | None = None
    low_read_pct: int = LOW_READ_PCT


CellStatus = Literal["ok", "low", "np", "cmt", "int", "na"]


class CellOut(Camel):
    status: CellStatus
    value: int | None = None
    text: str


class PubRowOut(Camel):
    name: str
    grp: str
    internal: bool
    cells: list[CellOut]


class PubCounts(Camel):
    np: int
    low: int
    cmt: int


class PublicationResponse(Camel):
    rows: list[PubRowOut]
    counts: PubCounts
    reply_pct: int | None = None


def classify(c: CellIn, low_read: int = LOW_READ_PCT) -> CellOut:
    """单元格状态：已发且查阅率 < 30% → 低查阅（发了没人看）。"""
    if c.state == "PUB":
        v = c.value or 0
        return CellOut(status="low", value=v, text=f"低查阅 {v}%") if v < low_read else CellOut(status="ok", value=v, text=f"已发 {v}%")
    if c.state == "NP":
        return CellOut(status="np", text="应公开未公开")
    if c.state == "CMT":
        return CellOut(status="cmt", value=c.value, text=f"意见 {c.value or 0} 条未复")
    if c.state == "INT":
        return CellOut(status="int", text="仅内部")
    return CellOut(status="na", text="—")


@router.post("/publication", response_model=PublicationResponse, response_model_by_alias=True)
def publication(req: PublicationRequest) -> PublicationResponse:
    """公开目录 × 受众：三类问题计数（该公开未公开 / 发了没人看 / 意见集中未答复）与答复率。"""
    rows, counts = [], {"np": 0, "low": 0, "cmt": 0}
    for r in req.rows:
        cells = [classify(c, req.low_read_pct) for c in r.cells]
        for c in cells:
            if c.status in counts:
                counts[c.status] += 1
        rows.append(PubRowOut(name=r.name, grp=r.grp, internal=r.grp == "仅内部" or all(c.status == "int" for c in cells), cells=cells))
    reply = None
    if req.status is not None and req.status.opinions:
        reply = round(req.status.replied / req.status.opinions * 100)
    return PublicationResponse(rows=rows, counts=PubCounts(**counts), reply_pct=reply)


# ---------------------------------------------------------------- B1 本院全息图


class OwnGroupIn(Camel):
    code: str
    name: str
    cases: int = Field(ge=0)
    avg_diff: float
    avg_cost: float = Field(ge=0)


class PeerGroupIn(Camel):
    code: str
    avg_cases: float = Field(ge=0)
    avg_diff: float
    avg_cost: float = Field(ge=0)


class IndicatorIn(Camel):
    name: str
    value: str
    unit: str = ""
    pct: int = Field(ge=0, le=100)
    higher_worse: bool


class LedgerIn(Camel):
    ledger_wan: float
    drg_pay_wan: float
    cases: int = Field(gt=0)
    diff_pct: int = Field(ge=0, le=100)


class TrendIn(Camel):
    month: str
    avg_diff: float


class HospitalRequest(Camel):
    own: list[OwnGroupIn]
    peers: list[PeerGroupIn]
    peer_count: int = Field(ge=0)
    indicators: list[IndicatorIn]
    ledger: LedgerIn
    trend: list[TrendIn]
    min_cases: int = MIN_CASES
    min_peers: int = MIN_PEERS


class OwnBubble(Camel):
    code: str
    name: str
    short_name: str
    cases: int
    avg_diff: float
    radius: float
    direction: Literal["逆差", "结余"]
    faded: bool
    peer_diff: float | None = None


class PeerBubble(Camel):
    code: str
    cases: float
    avg_diff: float
    radius: float


class Merged(Camel):
    count: int
    cases: int
    avg_diff: float


class IndicatorOut(Camel):
    name: str
    value: str
    unit: str
    pct: int
    tone: Literal["warning", "primary"]
    note: str


class Indicators(Camel):
    suppressed: bool
    message: str
    rows: list[IndicatorOut]


class LedgerOut(Camel):
    ledger_wan: float
    drg_pay_wan: float
    deviation_wan: float
    direction: Literal["逆差", "结余"]
    deviation_pct: float
    avg_diff: float
    diff_pct: int


class TrendOut(Camel):
    month: str
    avg_diff: float
    height_pct: float


class TopOut(Camel):
    code: str
    short_name: str
    cases: int
    avg_diff: float


class HospitalResponse(Camel):
    own: list[OwnBubble]
    peers: list[PeerBubble]
    merged: Merged | None
    axis: Axis
    indicators: Indicators
    ledger: LedgerOut
    trend: list[TrendOut]
    top: list[TopOut]


@router.post("/hospital", response_model=HospitalResponse, response_model_by_alias=True)
def hospital(req: HospitalRequest) -> HospitalResponse:
    """本院全息：小样本并入“其他”、同级灰色背景、六项分位着色、记账偏离与逆差贡献 Top 4。"""
    big = [g for g in req.own if g.cases >= req.min_cases]
    small = [g for g in req.own if g.cases < req.min_cases]
    peer_by = {p.code: p for p in req.peers}
    own = [OwnBubble(code=g.code, name=g.name, short_name=short_name(g.name), cases=g.cases, avg_diff=g.avg_diff,
                     radius=radius_small(g.avg_cost), direction="逆差" if g.avg_diff > 0 else "结余",
                     faded=abs(g.avg_diff) < FADE_DIFF_YUAN, peer_diff=peer_by[g.code].avg_diff if g.code in peer_by else None)
           for g in big]
    # 灰色背景只保留与本院可对照的病组（不暴露本院小样本病组）
    shown = {g.code for g in big}
    peers = [PeerBubble(code=p.code, cases=p.avg_cases, avg_diff=p.avg_diff, radius=radius_small(p.avg_cost))
             for p in req.peers if p.code in shown]
    merged = None
    if small:
        n = sum(g.cases for g in small)
        avg = round(sum(g.cases * g.avg_diff for g in small) / n) if n else 0
        merged = Merged(count=len(small), cases=n, avg_diff=avg)

    max_cases = max([g.cases for g in big] + [p.cases for p in peers] + [1])
    max_diff = max([abs(g.avg_diff) for g in big] + [abs(p.avg_diff) for p in peers] + [1])
    ax = axis(max_cases, max_diff)

    if req.peer_count < req.min_peers:
        ind = Indicators(suppressed=True, message=f"同级机构不足 {req.min_peers} 家,不输出同级分位", rows=[])
    else:
        rows = []
        for i in req.indicators:
            hot = i.higher_worse and i.pct >= 70
            rows.append(IndicatorOut(name=i.name, value=i.value, unit=i.unit, pct=i.pct, tone="warning" if hot else "primary",
                                     note="高于同级 P70,关注" if hot else ""))
        ind = Indicators(suppressed=False, message="", rows=rows)

    lg = req.ledger
    dev = round(lg.ledger_wan - lg.drg_pay_wan, 1)
    ledger = LedgerOut(ledger_wan=lg.ledger_wan, drg_pay_wan=lg.drg_pay_wan, deviation_wan=abs(dev),
                       direction="逆差" if dev > 0 else "结余",
                       deviation_pct=round((lg.drg_pay_wan - lg.ledger_wan) / lg.ledger_wan * 100, 1) if lg.ledger_wan else 0.0,
                       avg_diff=round(dev * 10_000 / lg.cases), diff_pct=lg.diff_pct)

    tmax = max([abs(t.avg_diff) for t in req.trend] + [1])
    t_step = nice_step(tmax / 5)
    t_axis = t_step * math.ceil(tmax / t_step)
    trend = [TrendOut(month=t.month, avg_diff=t.avg_diff, height_pct=round(abs(t.avg_diff) / t_axis * 100, 1)) for t in req.trend]

    top = sorted((g for g in big if g.avg_diff > 0), key=lambda g: g.avg_diff * g.cases, reverse=True)[:4]
    return HospitalResponse(own=own, peers=peers, merged=merged, axis=ax, indicators=ind, ledger=ledger, trend=trend,
                            top=[TopOut(code=g.code, short_name=short_name(g.name), cases=g.cases, avg_diff=g.avg_diff) for g in top])
