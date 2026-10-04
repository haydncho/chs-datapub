"""发布工作流（A8）：定向范围覆盖、分受众版本。路由前缀 /v1/publish。"""

from __future__ import annotations

from fastapi import APIRouter

router = APIRouter(prefix="/v1/publish", tags=["publish"])
