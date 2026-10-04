"""县区 / 省级 / 外部监督（C1 / C2 / C3）。路由前缀 /v1/regional。

- ``/county``：C1 县区视图——本县区机构清单质控关注标记、各县区按综合考核得分排名、医共体 14 项监测指标判级。
- ``/province``：C2 省级汇总——逾期判定、排序口径（期次 / 日期按时间序）、4 项 KPI。

口径约定：百分比、金额的展示舍入一律「四舍五入」（Decimal ROUND_HALF_UP），避免二进制浮点在 x.x5 处的误差。
"""

from __future__ import annotations

import re
from datetime import date
from decimal import ROUND_HALF_UP, Decimal
from typing import Literal

from fastapi import APIRouter
from pydantic import Field

from ..models import Camel

router = APIRouter(prefix="/v1/regional", tags=["regional"])
OPTS = {"response_model_by_alias": True}


def r1(x: float) -> float:
    """保留 1 位小数，四舍五入。"""
    return float(Decimal(str(x)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP))


def r0(x: float) -> int:
    return int(Decimal(str(x)).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def money(v: float, signed: bool) -> str:
    """金额展示：千分位；signed 时正数带 +，负数一律用 −（U+2212）。"""
    n = r0(v)
    sign = "−" if n < 0 else ("+" if signed and n > 0 else "")
    return f"{sign}{abs(n):,}"


# ================================================================ C1 县区视图


class CountyInstitutionIn(Camel):
    name: str
    level: str
    cases: int = Field(ge=0)
    avg_diff: float
    list_qc_pct: float = Field(ge=0, le=100)
    cost_pctl: int = Field(ge=0, le=100)


class CountyIn(Camel):
    name: str
    avg_diff: float
    list_qc_pct: float = Field(ge=0, le=100)
    #: 县区医保综合考核得分（排名口径）
    score: float
    is_self: bool = Field(default=False, alias="self")


class MonitorIn(Camel):
    name: str
    value: float
    unit: Literal["%", "元"]
    #: 金额是否带正负号（例均基金差额）
    signed: bool = False
    #: high = 越高越好；low = 越低越好
    direction: Literal["high", "low"]
    #: 关注线 / 预警线（按方向越线即判级）
    warn: float
    alarm: float


class CountyRequest(Camel):
    institutions: list[CountyInstitutionIn]
    counties: list[CountyIn]
    indicators: list[MonitorIn]
    #: 清单质控率低于该值标橙
    qc_threshold: float = 95.0


class CountyInstitutionOut(Camel):
    name: str
    level: str
    cases: int
    avg_diff: float
    avg_diff_text: str
    list_qc_pct: float
    cost_pctl: int
    qc_low: bool


class CountyOut(Camel):
    name: str
    rank: int
    avg_diff: float
    avg_diff_text: str
    list_qc_pct: float
    score: float
    is_self: bool = Field(alias="self")


class MonitorOut(Camel):
    name: str
    display: str
    status: Literal["ok", "warn", "bad"]
    #: 判级规则说明（悬停提示）
    rule: str


class CountyResponse(Camel):
    institutions: list[CountyInstitutionOut]
    counties: list[CountyOut]
    indicators: list[MonitorOut]
    status_counts: dict[str, int]


def fmt_monitor(m: MonitorIn, v: float) -> str:
    if m.unit == "%":
        return f"{r1(v):.1f}%"
    return f"{money(v, m.signed)} 元"


def classify(m: MonitorIn) -> Literal["ok", "warn", "bad"]:
    """越过预警线 → 预警；越过关注线 → 关注；否则正常。"""
    if m.direction == "high":
        return "bad" if m.value < m.alarm else "warn" if m.value < m.warn else "ok"
    return "bad" if m.value > m.alarm else "warn" if m.value > m.warn else "ok"


def rank_counties(counties: list[CountyIn]) -> list[CountyOut]:
    """按综合考核得分降序排名；同分并列（竞赛排名 1,1,3）。"""
    ordered = sorted(counties, key=lambda c: (-c.score, c.name))
    out: list[CountyOut] = []
    prev: float | None = None
    rank = 0
    for i, c in enumerate(ordered, start=1):
        if c.score != prev:
            rank, prev = i, c.score
        out.append(CountyOut(name=c.name, rank=rank, avg_diff=c.avg_diff, avg_diff_text=f"{money(c.avg_diff, True)} 元",
                             list_qc_pct=r1(c.list_qc_pct), score=c.score, is_self=c.is_self))
    return out


@router.post("/county", response_model=CountyResponse, **OPTS)
def county(req: CountyRequest) -> CountyResponse:
    """C1：机构清单质控关注标记 + 县区排名 + 医共体监测指标判级。"""
    inst = [CountyInstitutionOut(name=i.name, level=i.level, cases=i.cases, avg_diff=i.avg_diff,
                                 avg_diff_text=f"{money(i.avg_diff, True)} 元", list_qc_pct=r1(i.list_qc_pct),
                                 cost_pctl=i.cost_pctl, qc_low=i.list_qc_pct < req.qc_threshold) for i in req.institutions]
    indicators: list[MonitorOut] = []
    counts = {"ok": 0, "warn": 0, "bad": 0}
    for m in req.indicators:
        st = classify(m)
        counts[st] += 1
        cmp = "<" if m.direction == "high" else ">"
        rule = f"关注线 {cmp} {fmt_monitor(m, m.warn)} · 预警线 {cmp} {fmt_monitor(m, m.alarm)}"
        indicators.append(MonitorOut(name=m.name, display=fmt_monitor(m, m.value), status=st, rule=rule))
    return CountyResponse(institutions=inst, counties=rank_counties(req.counties), indicators=indicators, status_counts=counts)


# ================================================================ C2 省级汇总

SortKey = Literal["name", "period", "date", "signPct", "readPct", "replyPct", "balancePct", "coveragePct", "avgDiff"]


class RegionIn(Camel):
    name: str
    is_self: bool = Field(default=False, alias="self")
    #: 最近发布期次，如「2026年8月 月告知」「2026年第二季度 季公布」
    last_period: str
    last_date: date
    #: 下一期应发布截止日；数据截止日晚于它（且尚未发布）即逾期
    next_due: date
    sign_pct: float
    prev_sign_pct: float
    read_pct: float
    reply_pct: float
    balance_pct: float
    #: 统筹基金当期收入（亿元），全省结余率按收入加权
    fund_income: float = Field(gt=0)
    coverage_pct: float
    avg_diff: float


class ProvinceRequest(Camel):
    as_of: date
    regions: list[RegionIn] = Field(min_length=1)
    sort: SortKey = "signPct"
    dir: Literal["asc", "desc"] = "desc"
    #: 查阅率 / 意见答复率低于该值视为偏低
    low_read: float = 60.0
    low_reply: float = 60.0


class RegionOut(Camel):
    name: str
    is_self: bool = Field(alias="self")
    last_period: str
    date_label: str
    overdue: bool
    overdue_days: int
    sign_pct: float
    read_pct: float
    read_low: bool
    reply_pct: float
    balance_pct: float
    deficit: bool
    coverage_pct: float
    avg_diff: float
    avg_diff_text: str


class OverdueOut(Camel):
    name: str
    days: int


class ProvinceKpis(Camel):
    on_time: int
    total: int
    overdue: list[OverdueOut]
    avg_sign_pct: float
    sign_delta_pt: float
    avg_reply_pct: float
    low_reply_count: int
    balance_pct: float
    deficit_count: int


class ProvinceResponse(Camel):
    rows: list[RegionOut]
    kpis: ProvinceKpis
    sort: str
    dir: str


_PERIOD_RE = re.compile(r"(\d{4})年(?:(\d{1,2})月|第([一二三四])季度)?")
_QUARTER_END = {"一": 3, "二": 6, "三": 9, "四": 12}


def period_key(label: str) -> tuple[int, int]:
    """期次时间序：月告知取当月，季公布取季末月，年报取 12 月；无法识别的排最前。"""
    m = _PERIOD_RE.search(label)
    if not m:
        return (0, 0)
    year = int(m.group(1))
    if m.group(2):
        return (year, int(m.group(2)))
    if m.group(3):
        return (year, _QUARTER_END[m.group(3)])
    return (year, 12)


def overdue_days(r: RegionIn, as_of: date) -> int:
    """逾期天数 = 数据截止日 − 下一期应发布截止日（未到期为 0）。"""
    return max(0, (as_of - r.next_due).days)


def _sort_value(r: RegionIn, key: SortKey):
    return {
        "name": r.name,
        "period": period_key(r.last_period),
        "date": r.last_date,
        "signPct": r.sign_pct,
        "readPct": r.read_pct,
        "replyPct": r.reply_pct,
        "balancePct": r.balance_pct,
        "coveragePct": r.coverage_pct,
        "avgDiff": r.avg_diff,
    }[key]


def sort_regions(regions: list[RegionIn], key: SortKey, direction: str) -> list[RegionIn]:
    """稳定排序：先按名称排好作次序键，再按所选列升 / 降序（同值保持名称升序）。"""
    base = sorted(regions, key=lambda r: r.name)
    if direction == "desc":
        # reverse=True 会把同值行的次序也倒过来；用取反保持同值按名称升序
        groups: dict = {}
        for r in base:
            groups.setdefault(_sort_value(r, key), []).append(r)
        return [r for k in sorted(groups, reverse=True) for r in groups[k]]
    return sorted(base, key=lambda r: _sort_value(r, key))


@router.post("/province", response_model=ProvinceResponse, **OPTS)
def province(req: ProvinceRequest) -> ProvinceResponse:
    """C2：逐统筹区逾期判定 + 指定列排序 + 4 项 KPI（仅统筹区汇总层，无机构级字段）。"""
    regs = req.regions
    n = len(regs)
    rows: list[RegionOut] = []
    for r in sort_regions(regs, req.sort, req.dir):
        od = overdue_days(r, req.as_of)
        rows.append(RegionOut(
            name=r.name, is_self=r.is_self, last_period=r.last_period,
            date_label=f"逾期 {od} 天" if od else r.last_date.strftime("%m-%d"),
            overdue=od > 0, overdue_days=od,
            sign_pct=r1(r.sign_pct), read_pct=r1(r.read_pct), read_low=r.read_pct < req.low_read,
            reply_pct=r1(r.reply_pct), balance_pct=r1(r.balance_pct), deficit=r.balance_pct < 0,
            coverage_pct=r1(r.coverage_pct), avg_diff=r.avg_diff, avg_diff_text=f"{money(r.avg_diff, True)} 元",
        ))
    late = sorted((OverdueOut(name=r.name, days=overdue_days(r, req.as_of)) for r in regs if overdue_days(r, req.as_of) > 0),
                  key=lambda o: (-o.days, o.name))
    avg_sign = sum(r.sign_pct for r in regs) / n
    prev_sign = sum(r.prev_sign_pct for r in regs) / n
    income = sum(r.fund_income for r in regs)
    kpis = ProvinceKpis(
        on_time=n - len(late), total=n, overdue=late,
        avg_sign_pct=r1(avg_sign), sign_delta_pt=r1(avg_sign - prev_sign),
        avg_reply_pct=r1(sum(r.reply_pct for r in regs) / n),
        low_reply_count=sum(1 for r in regs if r.reply_pct < req.low_reply),
        balance_pct=r1(sum(r.balance_pct * r.fund_income for r in regs) / income),
        deficit_count=sum(1 for r in regs if r.balance_pct < 0),
    )
    return ProvinceResponse(rows=rows, kpis=kpis, sort=req.sort, dir=req.dir)
