"""机构门户分析（B2 / B3 / B7）。路由前缀 /v1/portal-analysis。"""

from __future__ import annotations

from fastapi import APIRouter

router = APIRouter(prefix="/v1/portal-analysis", tags=["portal-analysis"])
