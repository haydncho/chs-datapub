"""请求 / 响应模型。JSON 字段统一 camelCase（与 server 约定一致）。"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class HealthResponse(Camel):
    status: str
    version: str


# ---------------------------------------------------------------- 质量校验（A3）


class SourceIn(Camel):
    name: str
    status: Literal["OK", "LATE", "PART"]
    quality_score: int = Field(ge=0, le=100)


class OrgQualityIn(Camel):
    org: str
    comorbidity_pct: float = Field(ge=0, le=100)
    list_qc_pct: float = Field(ge=0, le=100)


class QualityRequest(Camel):
    sources: list[SourceIn]
    orgs: list[OrgQualityIn]
    #: 合并症编码率 / 清单质控率的关注阈值（低于即标橙）
    comorbidity_threshold: float = 60.0
    list_qc_threshold: float = 95.0


class OrgFlag(Camel):
    org: str
    comorbidity_low: bool
    list_qc_low: bool


class QualityResponse(Camel):
    passed: bool
    #: 及时性 = 按时到数的数据源占比（%）
    timeliness_pct: float
    flags: list[OrgFlag]
    blocking: list[str]
    message: str


# ---------------------------------------------------------------- 专题归因（A7）


class BehaviorIn(Camel):
    name: str
    rate_pct: float
    with_avg: float = Field(gt=0)
    without_avg: float = Field(gt=0)


class BehaviorRequest(Camel):
    rows: list[BehaviorIn]
    #: 费用倍率 ≥ 该值标红
    high_multiplier: float = 1.5


class BehaviorOut(Camel):
    name: str
    rate_pct: float
    with_avg: float
    without_avg: float
    multiplier: float
    level: Literal["high", "elevated"]


class BehaviorResponse(Camel):
    rows: list[BehaviorOut]
    top_multiplier: str
    top_rate: str


class WaterfallRequest(Camel):
    city_mean: float = Field(gt=0)
    patient_diff: float
    behavior_diff: float


class WaterfallBar(Camel):
    label: str
    base: float
    height: float
    value: float
    kind: Literal["total", "delta"]


class WaterfallResponse(Camel):
    bars: list[WaterfallBar]
    total_gap: float
    patient_share_pct: float
    behavior_share_pct: float
    axis_max: float


class DraftRequest(Camel):
    section: int = Field(ge=1, le=7)
    facts: dict


class DraftResponse(Camel):
    text: str
    provider: str
