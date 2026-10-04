"""病种专题（A7）：关键行为费用倍率、差异归因瀑布、七段文稿初稿。

文稿初稿为规则模板生成（provider = "rule"）；接入大模型时在 draft() 中替换，
输出一律标记「待人工审定」，由 server 控制审定流程。
"""

from __future__ import annotations

import math

from .models import (
    BehaviorOut,
    BehaviorRequest,
    BehaviorResponse,
    DraftRequest,
    DraftResponse,
    WaterfallBar,
    WaterfallRequest,
    WaterfallResponse,
)


def _round_half_up(x: float, nd: int = 2) -> float:
    q = 10**nd
    return math.floor(x * q + 0.5) / q


def behaviors(req: BehaviorRequest) -> BehaviorResponse:
    rows = [
        BehaviorOut(
            name=r.name,
            rate_pct=r.rate_pct,
            with_avg=r.with_avg,
            without_avg=r.without_avg,
            multiplier=_round_half_up(r.with_avg / r.without_avg),
            level="high" if r.with_avg / r.without_avg >= req.high_multiplier else "elevated",
        )
        for r in req.rows
    ]
    top_m = max(rows, key=lambda r: r.multiplier).name if rows else ""
    top_r = max(rows, key=lambda r: r.rate_pct).name if rows else ""
    return BehaviorResponse(rows=rows, top_multiplier=top_m, top_rate=top_r)


def _nice_ceil(v: float) -> float:
    """坐标轴上限取「好看」的整数（1/2/2.5/5 × 10^n）。"""
    if v <= 0:
        return 1.0
    exp = 10 ** math.floor(math.log10(v))
    for m in (1, 1.5, 2, 2.5, 3, 4, 5, 10):
        if v <= m * exp:
            return m * exp
    return 10 * exp


def waterfall(req: WaterfallRequest) -> WaterfallResponse:
    """全市均值 → 患者差异 → 行为差异 → 偏离组均值。"""
    after_patient = req.city_mean + req.patient_diff
    deviant = after_patient + req.behavior_diff
    gap = req.patient_diff + req.behavior_diff
    bars = [
        WaterfallBar(label="全市均值", base=0, height=req.city_mean, value=req.city_mean, kind="total"),
        WaterfallBar(label="患者差异", base=min(req.city_mean, after_patient), height=abs(req.patient_diff), value=req.patient_diff, kind="delta"),
        WaterfallBar(label="行为差异", base=min(after_patient, deviant), height=abs(req.behavior_diff), value=req.behavior_diff, kind="delta"),
        WaterfallBar(label="偏离组均值", base=0, height=deviant, value=deviant, kind="total"),
    ]
    patient_share = round(req.patient_diff / gap * 100) if gap else 0
    return WaterfallResponse(
        bars=bars,
        total_gap=gap,
        patient_share_pct=patient_share,
        behavior_share_pct=100 - patient_share if gap else 0,
        axis_max=_nice_ceil(max(deviant, req.city_mean) * 1.05),
    )


def _n(v: float) -> str:
    return f"{v:,.0f}"


def _cn(n: int) -> str:
    return "零一二三四五六七八九十"[n] if 0 <= n <= 10 else str(n)


def _signed(v: float) -> str:
    return ("+" if v >= 0 else "−") + _n(abs(v))


def draft(req: DraftRequest) -> DraftResponse:
    f = req.facts
    s = req.section
    if s == 1:
        o = f["overview"]
        top = o["levels"][0]
        text = (
            f"本季度 {f['code']} 全市收治 {_n(o['cases'])} 例,次均总费用 {_n(o['avgCost'])} 元,"
            f"同比{'上升' if o['yoyPct'] >= 0 else '下降'} {abs(o['yoyPct'])}%。例均基金差额 {_signed(o['avgDiff'])} 元,"
            f"为本期逆差总额最大的病组。病例集中在{top['name']}机构,占 {top['pct']}%。"
        )
    elif s == 2:
        mix = {r["name"]: r["values"] for r in f["costMix"]["rows"]}
        dev, bench = mix["偏离组"], mix["标杆组"]
        dm, bm = dev[0] + dev[1], bench[0] + bench[1]
        text = (
            f"偏离组药品与耗材合计占比 {dm}%,{'高于' if dm >= bm else '低于'}标杆组 {abs(dm - bm)} 个百分点;"
            "治疗及护理类占比偏低,提示费用结构以检查与药品驱动为主。"
        )
    elif s == 3:
        rows = f["behaviors"]
        tm = max(rows, key=lambda r: r["multiplier"])
        tr = max(rows, key=lambda r: r["ratePct"])
        text = (
            f"{_cn(len(rows))}项行为中,“{tm['name']}”费用倍率最高(×{tm['multiplier']:.2f}),但发生率仅 {tm['ratePct']}%;"
            f"“{tr['name']}”发生率最高({tr['ratePct']}%),对总费用影响最大。"
        )
    elif s == 4:
        a = f["attribution"]
        gap = a["patientDiff"] + a["behaviorDiff"]
        text = (
            f"偏离组与全市均值相差 {_n(gap)} 元,其中患者差异约 {_n(a['patientDiff'])} 元,"
            f"行为差异约 {_n(a['behaviorDiff'])} 元。行为差异主要来自重复检查与辅助用药。"
        )
    elif s == 5:
        o = f["overview"]
        b = {r["metric"]: r for r in f["benchmark"]}
        cost = b.get("例均费用", {})
        rep = b.get("重复检查发生率", {})
        text = (
            f"标杆组 {o['benchOrgs']} 家机构例均费用 {cost.get('bench', '—')},偏离组 {o['deviantOrgs']} 家为 {cost.get('deviant', '—')};"
            f"偏离组重复检查发生率是标杆组的 {str(rep.get('gap', '—')).lstrip('×')} 倍。"
        )
    elif s == 6:
        opt = f["optimization"]
        text = f"按行为差异占比测算,理论优化空间年化约 {_n(opt['totalWan'])} 万元。该数值为理论测算,不作为控费指标下达。"
    else:
        text = "建议医保侧复核分组边界并完善审核规则;医院侧推进检查结果互认与辅助用药管理。"
    return DraftResponse(text=text, provider="rule")
