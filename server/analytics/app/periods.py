"""Month arithmetic and data-batch ids shared by the analytics modules (pure)."""

from __future__ import annotations

import hashlib
from datetime import date
from typing import Iterable


def month_start(d: date) -> date:
    return d.replace(day=1)


def add_months(d: date, n: int) -> date:
    """First day of the month ``n`` months after ``d`` (negative = before)."""
    y, m = divmod(d.year * 12 + (d.month - 1) + n, 12)
    return date(y, m + 1, 1)


def last_months(end: date, n: int = 12) -> list[date]:
    """The ``n`` month starts ending at (and including) ``end``'s month, oldest first."""
    e = month_start(end)
    return [add_months(e, k) for k in range(-(n - 1), 1)]


def batch_id(period: date | None, rows: Iterable[object]) -> str:
    """Stable id of a data batch: ``YYYYMM-xxxxxx`` (period + digest of the input rows).

    The same inputs always give the same id; any change in the data changes it.
    """
    h = hashlib.sha1()
    for r in rows:
        h.update(repr(r).encode())
        h.update(b"\n")
    head = f"{period:%Y%m}" if period else "000000"
    return f"{head}-{h.hexdigest()[:6]}"
