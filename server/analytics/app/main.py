"""医保数据公开平台 · analytics service (FastAPI).

Reached by the browser only through the core API proxy
(/api/v1/analytics/** → this service's /analytics/**). Read-only on the database.
"""

from __future__ import annotations

from datetime import date, datetime, timezone

from fastapi import FastAPI, HTTPException, Query

from . import alerts, db, drg
from .periods import add_months, batch_id

app = FastAPI(title="yb-analytics", version="0.2.0")


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


@app.get("/analytics/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/analytics/drg/panorama")
def drg_panorama() -> dict:
    return drg.panorama(db.load_groups())


@app.get("/analytics/topics/recommend")
def topics_recommend(top: int = Query(8, ge=1, le=50)) -> list[dict]:
    """A6 选题池 candidates (``A6Topic`` shape + sub-scores + 方法卡 inputs), best first."""
    groups = db.load_groups()
    batch = batch_id(db.latest_series_period(), sorted((g.code, g.cases, g.diff_per_case, g.cost_per_case) for g in groups))
    return drg.recommend(groups, top=top, batch=batch)


@app.get("/analytics/alerts/evaluate")
def alerts_evaluate(period: str | None = Query(None, pattern=r"^\d{4}-\d{2}$", description="YYYY-MM; default = latest")) -> dict:
    """Run the 预警规则 over the indicator series; triggered alerts in the A11 ``alerts`` shape."""
    p = None
    if period:
        try:
            p = date(int(period[:4]), int(period[5:]), 1)
        except ValueError as e:
            raise HTTPException(422, f"invalid period: {period}") from e
    end = p or db.latest_series_period()
    # 12 months of trend + the previous month for 环比
    series = db.load_series(add_months(end, -12) if end else None)
    out = alerts.evaluate(db.load_rules(), series, db.load_orgs(), db.load_alert_state(), period=p)
    out["computedAt"] = _now()
    return out
