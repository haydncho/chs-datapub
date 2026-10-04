"""预警规则引擎 (A11): pure functions over monthly indicator series, no I/O.

Rule kinds (``alert_rule.kind``):

* ``mom_pct`` — 环比: (current − previous month) / previous × 100 above ``threshold`` %
  (a negative threshold means a drop: below ``threshold`` %)
* ``gt_p90``  — current value above the P90 of the peer group (every other institution
  reporting the metric in the same period; needs ``min_peers`` peers)
* ``gt`` / ``lt`` — absolute thresholds

Each rule carries its own ``level`` (high / mid / low). Only the evaluated period
(by default the latest period in the data) can trigger; earlier months form the trend.
Output matches ``A11Alert`` in ``web/src/mock/A11.ts``.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Iterable

from .periods import add_months, batch_id, last_months, month_start

KINDS = ("mom_pct", "gt_p90", "gt", "lt")
LEVEL_RANK = {"high": 0, "mid": 1, "low": 2}
STATUSES = ("unsent", "sent", "ack")


@dataclass(frozen=True)
class Rule:
    id: str
    metric: str
    label: str
    kind: str
    threshold: float | None
    level: str
    unit: str = ""
    fmt: str = "num"  # pct / yuan / num
    decimals: int = 1
    min_peers: int = 5


@dataclass(frozen=True)
class Point:
    org_id: str
    metric: str
    period: date
    value: float


# ── formatting ────────────────────────────────────────────────────────────────

MINUS = "−"


def _num(v: float, d: int) -> str:
    return f"{abs(v):,.{d}f}"


def _trim(v: float, d: int) -> str:
    """Threshold numbers: up to ``d`` decimals, trailing zeros dropped (95.0 → 95)."""
    s = f"{abs(v):,.{max(d, 0)}f}"
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return s


def _round(v: float, d: int) -> float | int:
    """Round for display; whole numbers stay ints (41, not 41.0) when ``d`` is 0."""
    return int(round(v)) if d <= 0 else round(v, d)


def _sign(v: float) -> str:
    return "+" if v > 0 else MINUS if v < 0 else ""


def format_value(rule: Rule, v: float, *, trim: bool = False) -> str:
    """Display a metric value: ``8.7%``, ``+1,620``, ``0.86``."""
    body = _trim(v, rule.decimals) if trim else _num(v, rule.decimals)
    if rule.fmt == "pct":
        return ("-" if v < 0 else "") + body + "%"
    if rule.fmt == "yuan":
        return _sign(v) + body
    return ("-" if v < 0 else "") + body


def format_threshold(rule: Rule, ref: float | None = None) -> str:
    """Trigger condition text: ``环比 > 15%``, ``> P90 (6.2%)``, ``< 95%``, ``> +1,500 元``."""
    if rule.kind == "mom_pct":
        t = rule.threshold or 0.0
        return f"环比 > {_trim(t, 1)}%" if t >= 0 else f"环比 < -{_trim(t, 1)}%"
    if rule.kind == "gt_p90":
        return f"> P90 ({format_value(rule, ref or 0.0)})"
    op = ">" if rule.kind == "gt" else "<"
    t = format_value(rule, rule.threshold or 0.0, trim=True)
    return f"{op} {t} 元" if rule.fmt == "yuan" else f"{op} {t}"


# ── statistics ────────────────────────────────────────────────────────────────

def percentile(values: Iterable[float], p: float) -> float:
    """Linear-interpolated percentile (numpy's default), ``p`` in [0, 100]."""
    xs = sorted(values)
    if not xs:
        raise ValueError("percentile of empty data")
    if not 0 <= p <= 100:
        raise ValueError("p must be within [0, 100]")
    k = (len(xs) - 1) * p / 100
    lo = int(k)
    hi = min(lo + 1, len(xs) - 1)
    return xs[lo] + (xs[hi] - xs[lo]) * (k - lo)


# ── rule checks ───────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class Hit:
    """One triggered rule: current value, the value shown, and the threshold line."""

    value: float
    shown: str
    threshold_text: str
    threshold_value: float


def check(rule: Rule, current: float | None, previous: float | None = None,
          peers: list[float] | None = None) -> Hit | None:
    """Evaluate one rule for one institution; ``None`` = not triggered (or not evaluable)."""
    if current is None:
        return None
    if rule.kind == "mom_pct":
        if previous is None or previous == 0 or rule.threshold is None:
            return None
        pct = (current - previous) / abs(previous) * 100
        t = rule.threshold
        if not (pct > t if t >= 0 else pct < t):
            return None
        shown = _sign(round(pct, 1)) + _num(pct, 1) + "%"
        return Hit(current, shown.replace(MINUS, "-"), format_threshold(rule), previous * (1 + t / 100))
    if rule.kind == "gt_p90":
        peers = peers or []
        if len(peers) < max(rule.min_peers, 1):
            return None
        p90 = round(percentile(peers, 90), rule.decimals)
        if not current > p90:
            return None
        return Hit(current, format_value(rule, current), format_threshold(rule, p90), p90)
    if rule.kind in ("gt", "lt"):
        if rule.threshold is None:
            return None
        hit = current > rule.threshold if rule.kind == "gt" else current < rule.threshold
        if not hit:
            return None
        return Hit(current, format_value(rule, current), format_threshold(rule), rule.threshold)
    return None  # unknown kind: never fires


# ── evaluation ────────────────────────────────────────────────────────────────

def latest_period(series: Iterable[Point]) -> date | None:
    ps = [p.period for p in series]
    return month_start(max(ps)) if ps else None


def evaluate(rules: list[Rule], series: list[Point], orgs: dict[str, str],
             existing: dict[tuple[str, str], tuple[str, str]] | None = None,
             period: date | None = None) -> dict:
    """Run every rule over the series for ``period`` (default: latest month in the data).

    * ``orgs`` — org id → display name
    * ``existing`` — (org name, metric label) → (alert id, status) from the core ``alert``
      table, so triggered alerts keep their ids and 提醒函 / 回执 state
    """
    existing = existing or {}
    period = month_start(period) if period else latest_period(series)
    months = last_months(period, 12) if period else []
    out: dict = {
        "periodKey": f"{period:%Y-%m}" if period else None,
        "months": [str(m.month) for m in months],
        "ruleCount": len(rules),
        "batch": batch_id(period, sorted((p.org_id, p.metric, p.period, p.value) for p in series)),
        "alerts": [],
    }
    if period is None:
        return out

    by_metric: dict[str, dict[str, dict[date, float]]] = {}
    for p in series:
        by_metric.setdefault(p.metric, {}).setdefault(p.org_id, {})[month_start(p.period)] = float(p.value)

    prev = add_months(period, -1)
    found = []
    for rule in rules:
        per_org = by_metric.get(rule.metric, {})
        current = {o: s[period] for o, s in per_org.items() if period in s}
        for org_id, v in current.items():
            peers = [x for o, x in current.items() if o != org_id]
            hit = check(rule, v, per_org[org_id].get(prev), peers)
            if hit is None:
                continue
            org = orgs.get(org_id, org_id)
            aid, status = existing.get((org, rule.label), (f"AL-{rule.id}-{org_id}", "unsent"))
            s = per_org[org_id]
            found.append((LEVEL_RANK.get(rule.level, 9), rule.id, org_id, {
                "id": aid,
                "org": org,
                "metric": rule.label,
                "value": hit.shown,
                "threshold": hit.threshold_text,
                "level": rule.level,
                "trend": [_round(s[m], rule.decimals) for m in months if m in s],
                "thresholdValue": _round(hit.threshold_value, rule.decimals),
                "unit": rule.unit,
                "status": status if status in STATUSES else "unsent",
                "ruleId": rule.id,
                "orgId": org_id,
                "kind": rule.kind,
            }))
    found.sort(key=lambda t: t[:3])
    out["alerts"] = [a for *_, a in found]
    return out
