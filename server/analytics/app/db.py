"""PostgreSQL access (shared database with the Java core service)."""

from __future__ import annotations

import os
from contextlib import contextmanager
from datetime import date

import psycopg

from .alerts import Point, Rule
from .drg import Group

DSN = os.environ.get("DATABASE_URL", "postgresql://ybdata:ybdata@localhost:5432/ybdata")


@contextmanager
def connect():
    with psycopg.connect(DSN, connect_timeout=5) as conn:
        yield conn


def load_groups() -> list[Group]:
    with connect() as conn:
        rows = conn.execute(
            "select code, name, cases, diff_per_case, cost_per_case from drg_group order by code"
        ).fetchall()
    return [Group(*r) for r in rows]


# ── A11 预警 rule engine inputs (read-only) ─────────────────────────────────


def load_rules() -> list[Rule]:
    with connect() as conn:
        rows = conn.execute(
            "select id, metric, label, kind, threshold, level, unit, fmt, decimals, min_peers"
            " from alert_rule where enabled order by id"
        ).fetchall()
    return [Rule(r[0], r[1], r[2], r[3], None if r[4] is None else float(r[4]), *r[5:]) for r in rows]


def load_series(since: date | None = None) -> list[Point]:
    sql = "select org_id, metric, period, value from indicator_series"
    with connect() as conn:
        rows = (conn.execute(sql + " where period >= %s order by 1, 2, 3", (since,)) if since
                else conn.execute(sql + " order by 1, 2, 3")).fetchall()
    return [Point(r[0], r[1], r[2], float(r[3])) for r in rows]


def load_orgs() -> dict[str, str]:
    with connect() as conn:
        return dict(conn.execute("select id, name from org").fetchall())


def load_alert_state() -> dict[tuple[str, str], tuple[str, str]]:
    """(org name, metric label) → (alert id, status) from the core-owned ``alert`` table."""
    with connect() as conn:
        rows = conn.execute("select id, org_name, metric, status from alert order by id").fetchall()
    return {(r[1], r[2]): (r[0], r[3]) for r in rows}


def latest_series_period() -> date | None:
    with connect() as conn:
        return conn.execute("select max(period) from indicator_series").fetchone()[0]
