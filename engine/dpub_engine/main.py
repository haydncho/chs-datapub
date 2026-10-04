"""FastAPI 应用入口：``uvicorn dpub_engine.main:app --port 8091``。"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from . import ENGINE_VERSION, quality, topic
from .models import (
    BehaviorRequest,
    BehaviorResponse,
    DraftRequest,
    DraftResponse,
    HealthResponse,
    QualityRequest,
    QualityResponse,
    WaterfallRequest,
    WaterfallResponse,
)

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="医保数据公开定向发布平台 分析引擎", version=ENGINE_VERSION)


def _loc_text(loc: Any) -> str:
    return ".".join(str(x) for x in (loc or ()) if x != "body")


@app.exception_handler(RequestValidationError)
async def _validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
    """请求校验失败 → 422：``{detail: "请求参数不合法：…"}``。"""
    parts = [f"{_loc_text(e.get('loc'))} {e.get('msg', '')}".strip() for e in exc.errors()[:10]]
    return JSONResponse(status_code=422, content={"detail": "请求参数不合法：" + "；".join(parts)})


OPTS = {"response_model_by_alias": True}


@app.get("/v1/health", response_model=HealthResponse, **OPTS)
def health() -> HealthResponse:
    return HealthResponse(status="UP", version=ENGINE_VERSION)


@app.post("/v1/quality/check", response_model=QualityResponse, **OPTS)
def quality_check(req: QualityRequest) -> QualityResponse:
    """归集质量校验：及时性、机构编码与清单质控关注项、阻断项。"""
    return quality.check(req)


@app.post("/v1/topic/behaviors", response_model=BehaviorResponse, **OPTS)
def topic_behaviors(req: BehaviorRequest) -> BehaviorResponse:
    """关键行为费用倍率 = 有该行为次均 / 无该行为次均。"""
    return topic.behaviors(req)


@app.post("/v1/topic/waterfall", response_model=WaterfallResponse, **OPTS)
def topic_waterfall(req: WaterfallRequest) -> WaterfallResponse:
    """差异归因瀑布：全市均值 → 患者差异 → 行为差异 → 偏离组均值。"""
    return topic.waterfall(req)


@app.post("/v1/topic/draft", response_model=DraftResponse, **OPTS)
def topic_draft(req: DraftRequest) -> DraftResponse:
    """七段式文稿初稿（规则模板）；结果须人工审定后才能进入发布包。"""
    return topic.draft(req)
