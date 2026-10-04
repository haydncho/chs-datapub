"""全息图（A2 / B1）：气泡全景、关键少数病组、图层。路由前缀 /v1/holo。"""

from __future__ import annotations

from fastapi import APIRouter

router = APIRouter(prefix="/v1/holo", tags=["holo"])
