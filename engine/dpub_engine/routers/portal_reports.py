"""机构门户报告与意见（B4 / B5 / B6 / D1）。路由前缀 /v1/portal-reports。

- ``POST /quiz/grade``：B6 随堂试题判分。正确答案由业务服务从库中取出后随作答一起传入，
  引擎只做判分与成绩汇总；业务服务只把判分结果（含作答后才公开的正确选项）返回给前端。
"""

from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel

router = APIRouter(prefix="/v1/portal-reports", tags=["portal-reports"])

# 及格线：正确率 ≥ 60%
PASS_RATIO = 0.6
LETTERS = "ABCDEFGH"


class _Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class QuizItem(_Camel):
    quiz_id: int
    option_count: int = Field(ge=2, le=8)
    picked: int = Field(ge=0)
    answer: int = Field(ge=0)

    @model_validator(mode="after")
    def _in_range(self) -> "QuizItem":
        if self.picked >= self.option_count or self.answer >= self.option_count:
            raise ValueError("选项下标超出范围")
        return self


class GradeRequest(_Camel):
    items: list[QuizItem] = Field(min_length=1, max_length=50)


class GradedItem(_Camel):
    quiz_id: int
    picked: int
    correct: bool
    correct_index: int
    result: str


class GradeResponse(_Camel):
    items: list[GradedItem]
    score: int
    total: int
    accuracy_pct: float
    passed: bool


def grade(req: GradeRequest) -> GradeResponse:
    """逐题判分：正确 → 「回答正确」；错误 → 「回答错误,正确答案为 X」；汇总得分与是否及格。"""
    items = [
        GradedItem(
            quiz_id=it.quiz_id,
            picked=it.picked,
            correct=it.picked == it.answer,
            correct_index=it.answer,
            result="回答正确" if it.picked == it.answer else f"回答错误,正确答案为 {LETTERS[it.answer]}",
        )
        for it in req.items
    ]
    score = sum(1 for x in items if x.correct)
    total = len(items)
    return GradeResponse(
        items=items,
        score=score,
        total=total,
        accuracy_pct=round(score / total * 100, 1),
        passed=score / total >= PASS_RATIO,
    )


@router.post("/quiz/grade", response_model=GradeResponse, response_model_by_alias=True)
def quiz_grade(req: GradeRequest) -> GradeResponse:
    """B6 随堂试题即时判分。"""
    return grade(req)
