"""数据归集质量校验（A3「完成质量校验」）。

规则：
- 及时性 = 已按时到数（含部分到数）的数据源 / 全部数据源；
- 存在「未按时到达」的数据源时校验不通过：依赖指标本期暂缓，不能进入报告生成；
- 机构级：合并症编码率 < 阈值、结算清单质控率 < 阈值 标记关注（不阻断）。
"""

from __future__ import annotations

from .models import OrgFlag, QualityRequest, QualityResponse


def check(req: QualityRequest) -> QualityResponse:
    total = len(req.sources)
    late = [s.name for s in req.sources if s.status == "LATE"]
    timeliness = round((total - len(late)) / total * 100, 1) if total else 0.0
    flags = [
        OrgFlag(
            org=o.org,
            comorbidity_low=o.comorbidity_pct < req.comorbidity_threshold,
            list_qc_low=o.list_qc_pct < req.list_qc_threshold,
        )
        for o in req.orgs
    ]
    watched = sum(1 for f in flags if f.comorbidity_low or f.list_qc_low)
    if late:
        msg = f"{len(late)} 个数据源未按时到达({'、'.join(late)}),依赖指标本期暂缓,校验未通过"
    else:
        msg = f"质量校验通过;{watched} 家机构编码或清单质控需关注"
    return QualityResponse(passed=not late, timeliness_pct=timeliness, flags=flags, blocking=late, message=msg)
