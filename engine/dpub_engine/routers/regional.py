"""县区 / 省级 / 外部监督（C1 / C2 / C3）。路由前缀 /v1/regional。"""

from __future__ import annotations

from fastapi import APIRouter

router = APIRouter(prefix="/v1/regional", tags=["regional"])
