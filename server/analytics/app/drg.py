"""DRG analytics: pure functions over per-group rows, no I/O.

Inputs are the city-wide groups from ``drg_group`` (cases, 例均基金差额,
次均费用). Outputs feed the 全景图 病组全景 and the A6 选题推荐.
"""

from __future__ import annotations

from dataclasses import dataclass
from math import sqrt
from statistics import median


@dataclass(frozen=True)
class Group:
    code: str
    name: str
    cases: int
    diff_per_case: int  # 元; positive = 逆差 (fund pays more than the DRG standard)
    cost_per_case: int  # 元

    @property
    def total_diff(self) -> int:
        return self.cases * self.diff_per_case


# quadrant labels used on the panorama
KEY_FEW = "关键少数"  # high volume, deficit
WATCH = "高额个例"  # low volume, deficit
STABLE = "稳定结余"  # high volume, surplus
MINOR = "低量结余"  # low volume, surplus


def quadrant(g: Group, case_median: float) -> str:
    high = g.cases >= case_median
    if g.diff_per_case > 0:
        return KEY_FEW if high else WATCH
    return STABLE if high else MINOR


def panorama(groups: list[Group]) -> dict:
    """Bubble panorama: x = cases, y = 例均差额, size = |总差额|, with median guides."""
    if not groups:
        return {"medianCases": 0, "medianDiff": 0, "deficitTotal": 0, "surplusTotal": 0, "groups": []}
    mc = median(g.cases for g in groups)
    md = median(g.diff_per_case for g in groups)
    return {
        "medianCases": mc,
        "medianDiff": md,
        "deficitTotal": sum(g.total_diff for g in groups if g.total_diff > 0),
        "surplusTotal": sum(g.total_diff for g in groups if g.total_diff < 0),
        "groups": [
            {
                "code": g.code,
                "name": g.name,
                "cases": g.cases,
                "diffPerCase": g.diff_per_case,
                "costPerCase": g.cost_per_case,
                "totalDiff": g.total_diff,
                "quadrant": quadrant(g, mc),
                # pulse ring on the cockpit for deficits above 100 万
                "alert": g.total_diff > 1_000_000,
            }
            for g in sorted(groups, key=lambda g: -abs(g.total_diff))
        ],
    }


# A6 scoring weights: 影响金额 / 偏离度 / 可干预性 / 数据就绪 (order of A6 ``dimLabels``)
WEIGHTS = {"impact": 0.4, "deviation": 0.25, "actionable": 0.2, "ready": 0.15}
DIMS = ("impact", "deviation", "actionable", "ready")
# surplus groups enter the pool only when the total surplus is large (疑似低码高编反向)
SURPLUS_MIN = 1_000_000
# groups whose coding quality is below the bar are not published (暂不输出)
READY_MIN = 85
DEFAULT_READY = 90.0
HIGH_COST = 30_000  # 次均费用 above this → mostly 市三级 cases


def _ratio(v: float, hi: float) -> float:
    """Square-root scale against the largest candidate (keeps the long tail readable), 0–100."""
    return 0.0 if hi <= 0 else max(0.0, min(100.0, sqrt(max(v, 0.0) / hi) * 100))


def _wan(v: float) -> str:
    return f"{abs(v) / 10_000:,.1f} 万"


def _signed(v: int) -> str:
    return ("+" if v > 0 else "−" if v < 0 else "") + f"{abs(v):,}"


def recommend(groups: list[Group], quality: dict[str, float] | None = None, top: int = 8,
              batch: str | None = None) -> list[dict]:
    """Rank 病种专题 candidates for the A6 选题池.

    Candidates are deficit groups (例均差额 > 0) plus surplus groups whose total surplus
    is at least ``SURPLUS_MIN`` (possible reverse up-coding). Four sub-scores, 0–100:

    * impact     — |总差额| against the largest candidate (square-root scale)
    * deviation  — |例均差额| / 次均费用 against the largest candidate (square-root scale)
    * actionable — favours mid-to-high volume groups where behaviour change pays off
    * ready      — coding-quality score per group (defaults to 90)

    Each item has the ``A6Topic`` shape (web/src/mock/A6.ts) plus the legacy fields
    (code, name, totalDiff, scores, publishable) and a ``method`` card with the inputs.
    """
    quality = quality or {}
    cands = [g for g in groups if g.diff_per_case > 0 or (g.diff_per_case < 0 and -g.total_diff >= SURPLUS_MIN)]
    cands = [g for g in cands if g.cost_per_case > 0]
    if not cands:
        return []
    max_imp = max(abs(g.total_diff) for g in cands)
    max_dev = max(abs(g.diff_per_case) / g.cost_per_case for g in cands)
    max_cases = max(g.cases for g in cands)
    top_deficit = max((g for g in cands if g.total_diff > 0), key=lambda g: g.total_diff, default=None)
    out = []
    for g in cands:
        dev = abs(g.diff_per_case) / g.cost_per_case
        s = {
            "impact": round(_ratio(abs(g.total_diff), max_imp)),
            "deviation": round(_ratio(dev, max_dev)),
            "actionable": round(min(100.0, g.cases / max_cases * 120)),
            "ready": round(quality.get(g.code, DEFAULT_READY)),
        }
        score = round(sum(s[k] * w for k, w in WEIGHTS.items()))
        deficit = g.diff_per_case > 0
        if not deficit:
            why = f"结余 {_signed(g.diff_per_case)} 元/例,疑似低码高编反向"
        elif g is top_deficit:
            why = f"逆差总额全市最大({_wan(g.total_diff)}),例均逆差 {_signed(g.diff_per_case)} 元"
        else:
            why = f"例均逆差 {_signed(g.diff_per_case)} 元,占次均费用 {dev * 100:.1f}%"
        audiences = ["市三级"] if g.cost_per_case >= HIGH_COST else ["市三级", "县三级"]
        if score >= 85:
            audiences.append("专家组")
        out.append({
            # A6Topic
            "id": f"T-{g.code}",
            "kind": "病种专题",
            "title": f"{g.code} {g.name}",
            "why": why,
            "score": score,
            "dims": [s[k] for k in DIMS],
            "source": "差额排行",
            "status": "open",
            "facts": [
                {"k": "逆差总额" if deficit else "结余总额", "v": _wan(g.total_diff)},
                {"k": "例均差额", "v": _signed(g.diff_per_case)},
                {"k": "病例数", "v": f"{g.cases:,}"},
                {"k": "差额占次均", "v": f"{dev * 100:.1f}%"},
            ],
            "audiences": audiences,
            "method": {
                "weights": dict(WEIGHTS),
                "inputs": {
                    "cases": g.cases,
                    "diffPerCase": g.diff_per_case,
                    "costPerCase": g.cost_per_case,
                    "totalDiff": g.total_diff,
                    "deviationPct": round(dev * 100, 2),
                    "quality": s["ready"],
                },
            },
            **({"batch": batch} if batch else {}),
            # legacy fields
            "code": g.code,
            "name": g.name,
            "totalDiff": g.total_diff,
            "scores": s,
            "publishable": s["ready"] >= READY_MIN,
        })
    out.sort(key=lambda r: (-r["score"], r["code"]))
    return out[:top]
