"""指标配置与智能推荐（A4 / A6）。路由前缀 /v1/indicator。"""

from __future__ import annotations

from fastapi import APIRouter

router = APIRouter(prefix="/v1/indicator", tags=["indicator"])
