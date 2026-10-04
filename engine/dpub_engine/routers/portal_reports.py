"""机构门户报告与意见（B4 / B5 / B6 / D1）。路由前缀 /v1/portal-reports。"""

from __future__ import annotations

from fastapi import APIRouter

router = APIRouter(prefix="/v1/portal-reports", tags=["portal-reports"])
