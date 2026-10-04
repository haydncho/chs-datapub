"""指标配置与智能推荐（A4 / A6）。路由前缀 /v1/indicator。

- 公式校验：解析公式编辑器文本（聚合函数、WHERE、GROUP BY），逐行报错，给出预计取数。
- 以机构身份预览：按「查看者 × 小样本阈值 × 仅内部」裁剪；机构身份只返回本院值与同级分位，不返回他院名称与数值。
- 选题综合得分、归因拆分（编码质量不达标暂不输出）、标杆分组（阈值步进实时重算）。

分位统一采用 Weibull 插值（位置 = q × (n + 1)，等同 Excel PERCENTILE.EXC，越界取端点）；
机构所处分位 = 秩 / (n + 1)（并列取平均秩）。
"""

from __future__ import annotations

import re
from typing import Literal

from fastapi import APIRouter
from pydantic import Field

from ..models import Camel

router = APIRouter(prefix="/v1/indicator", tags=["indicator"])

OPTS = {"response_model_by_alias": True}


# ================================================================ 分位工具


def quantile(values: list[float], q: float) -> float:
    """Weibull 插值分位（q ∈ [0, 1]）。"""
    xs = sorted(values)
    n = len(xs)
    if n == 0:
        raise ValueError("空样本")
    pos = q * (n + 1)
    if pos <= 1:
        return xs[0]
    if pos >= n:
        return xs[-1]
    lo = int(pos)
    frac = pos - lo
    return xs[lo - 1] + frac * (xs[lo] - xs[lo - 1])


def percentile_rank(values: list[float], v: float) -> int:
    """v 在样本中的分位（0–100）：秩 / (n + 1)，并列取平均秩；样本外的值按插值反推。"""
    xs = sorted(values)
    n = len(xs)
    below = sum(1 for x in xs if x < v)
    equal = sum(1 for x in xs if x == v)
    if equal:
        rank = below + (equal + 1) / 2
    elif below == 0:
        rank = 0.0
    elif below == n:
        rank = n + 1.0
    else:
        a, b = xs[below - 1], xs[below]
        rank = below + (v - a) / (b - a)
    return max(0, min(100, round(rank / (n + 1) * 100)))


def r1(x: float) -> float:
    return round(x + 1e-9, 1)


# ================================================================ 公式校验（A4 第 1 步）

FUNCS = {"SUM": 1, "COUNT": 1, "AVG": 1, "PCTL": 2, "RATIO": 2}
KEYWORDS = {"WHERE", "AND", "OR", "GROUP", "BY", "IN", "NOT"}
COMPARE = {"=", "!=", "≠", ">", "<", ">=", "<="}

_TOKEN = re.compile(
    r"""(?P<ws>[ \t\r]+)|(?P<nl>\n)|(?P<str>'[^'\n]*')|(?P<num>\d+(?:\.\d+)?%?)"""
    r"""|(?P<op>!=|>=|<=|[=≠><+\-*/÷×(),])"""
    r"""|(?P<id>[A-Za-z_一-鿿][A-Za-z0-9_一-鿿·]*)"""
)


class AtomIn(Camel):
    name: str
    #: 本期可取数的例数（用于「预计取数」）
    cases: int = 0


class FormulaRequest(Camel):
    formula: str = Field(max_length=4000)
    atoms: list[AtomIn]
    #: 左侧选中的维度；GROUP BY 只能引用这些维度
    dims: list[str]
    #: 维度全集（用于区分「未知维度」与「未选中维度」）
    all_dims: list[str] = []
    filter_fields: list[str]
    caliber: str = ""


class FormulaError(Camel):
    line: int
    message: str


class FormulaResponse(Camel):
    ok: bool
    name: str | None
    errors: list[FormulaError]
    atoms: list[str]
    functions: list[str]
    group_by: list[str]
    estimated_cases: int | None
    message: str


class _Tok:
    __slots__ = ("kind", "text", "line")

    def __init__(self, kind: str, text: str, line: int):
        self.kind, self.text, self.line = kind, text, line


class _ParseError(Exception):
    def __init__(self, line: int, message: str):
        super().__init__(message)
        self.line, self.message = line, message


def _tokenize(src: str) -> list[_Tok]:
    out: list[_Tok] = []
    line, i = 1, 0
    while i < len(src):
        m = _TOKEN.match(src, i)
        if not m:
            raise _ParseError(line, f"无法识别的字符「{src[i]}」")
        kind = m.lastgroup or ""
        text = m.group()
        if kind == "nl":
            line += 1
        elif kind == "id" and text.upper() in KEYWORDS | set(FUNCS):
            out.append(_Tok("kw" if text.upper() in KEYWORDS else "fn", text.upper(), line))
        elif kind != "ws":
            out.append(_Tok(kind, text, line))
        i = m.end()
    return out


class _Parser:
    def __init__(self, toks: list[_Tok], atoms: dict[str, int], dims: list[str], all_dims: list[str], fields: list[str]):
        self.t = toks
        self.i = 0
        self.atoms = atoms
        self.dims = dims
        self.all_dims = all_dims
        self.fields = fields
        self.used_atoms: list[str] = []
        self.used_funcs: list[str] = []
        self.count_atom: str | None = None
        self.div_atom: str | None = None
        self.group_by: list[str] = []
        self.errors: list[FormulaError] = []

    # ---- 工具
    def peek(self) -> _Tok | None:
        return self.t[self.i] if self.i < len(self.t) else None

    def last_line(self) -> int:
        return self.t[-1].line if self.t else 1

    def take(self) -> _Tok:
        tok = self.peek()
        if tok is None:
            raise _ParseError(self.last_line(), "公式不完整")
        self.i += 1
        return tok

    def expect(self, text: str, what: str) -> _Tok:
        tok = self.peek()
        if tok is None or tok.text != text:
            line = tok.line if tok else self.last_line()
            got = f",实际为「{tok.text}」" if tok else ""
            raise _ParseError(line, f"缺少{what}{got}")
        return self.take()

    def semantic(self, line: int, msg: str) -> None:
        self.errors.append(FormulaError(line=line, message=msg))

    # ---- 文法
    def parse(self) -> str:
        name_tok = self.take()
        if name_tok.kind != "id":
            raise _ParseError(name_tok.line, "第一行须为「指标名称 =」")
        self.expect("=", "等号「=」(第一行须为「指标名称 =」)")
        if self.peek() is None or self.at_clause():
            raise _ParseError(self.last_line(), "等号右侧缺少计算表达式")
        self.expr()
        if not self.used_funcs:
            self.semantic(name_tok.line, "表达式中至少需要一个聚合函数(SUM / COUNT / AVG / PCTL / RATIO)")
        tok = self.peek()
        if tok is not None and tok.text == "WHERE":
            self.take()
            self.where()
            tok = self.peek()
        if tok is not None and tok.text == "GROUP":
            self.take()
            self.expect("BY", "关键字 BY(应为 GROUP BY)")
            self.group()
            tok = self.peek()
        if tok is not None:
            raise _ParseError(tok.line, f"多余的内容「{tok.text}」")
        return name_tok.text

    def at_clause(self) -> bool:
        tok = self.peek()
        return tok is not None and tok.text in ("WHERE", "GROUP")

    def expr(self) -> None:
        self.term()
        while (tok := self.peek()) is not None and tok.text in ("+", "-"):
            self.take()
            self.term()

    def term(self) -> None:
        self.factor()
        while (tok := self.peek()) is not None and tok.text in ("*", "/", "÷", "×"):
            op = self.take()
            if op.text in ("/", "÷"):
                nxt = self.peek()
                if nxt is not None and nxt.kind == "fn" and self.i + 2 < len(self.t):
                    arg = self.t[self.i + 2]
                    if arg.kind == "id":
                        self.div_atom = arg.text
            self.factor()

    def factor(self) -> None:
        tok = self.take()
        if tok.kind == "num":
            return
        if tok.text == "(":
            self.expr()
            self.expect(")", "右括号「)」")
            return
        if tok.kind == "fn":
            self.call(tok)
            return
        if tok.kind == "id":
            if tok.text in self.atoms:
                raise _ParseError(tok.line, f"原子指标「{tok.text}」须放在聚合函数中引用,如 SUM({tok.text})")
            raise _ParseError(tok.line, f"未知原子指标「{tok.text}」")
        if tok.kind == "kw":
            raise _ParseError(tok.line, f"关键字「{tok.text}」位置不正确,表达式不完整")
        raise _ParseError(tok.line, f"此处应为数值、函数或括号,实际为「{tok.text}」")

    def atom_ref(self) -> str:
        tok = self.take()
        if tok.kind != "id":
            raise _ParseError(tok.line, f"函数参数应为原子指标,实际为「{tok.text}」")
        if tok.text not in self.atoms:
            self.semantic(tok.line, f"未知原子指标「{tok.text}」")
        elif tok.text not in self.used_atoms:
            self.used_atoms.append(tok.text)
        return tok.text

    def call(self, fn: _Tok) -> None:
        if fn.text not in self.used_funcs:
            self.used_funcs.append(fn.text)
        self.expect("(", f"函数 {fn.text} 的左括号「(」")
        a = self.atom_ref()
        if fn.text == "COUNT" and self.count_atom is None:
            self.count_atom = a
        if FUNCS[fn.text] == 2:
            self.expect(",", f"函数 {fn.text} 的第二个参数")
            if fn.text == "PCTL":
                p = self.take()
                if p.kind != "num" or not 0 < float(p.text.rstrip("%")) < 100:
                    raise _ParseError(p.line, "PCTL 第二个参数须为 1–99 之间的分位数")
            else:
                b = self.atom_ref()
                self.div_atom = self.div_atom or b
        self.expect(")", f"函数 {fn.text} 的右括号「)」")

    def where(self) -> None:
        self.cond()
        while (tok := self.peek()) is not None and tok.text in ("AND", "OR"):
            self.take()
            self.cond()

    def cond(self) -> None:
        f = self.take()
        if f.kind != "id":
            raise _ParseError(f.line, f"WHERE 条件应以过滤字段开头,实际为「{f.text}」")
        if f.text not in self.fields:
            self.semantic(f.line, f"未知过滤字段「{f.text}」")
        op = self.take()
        if op.text not in COMPARE:
            raise _ParseError(op.line, f"过滤条件「{f.text}」缺少比较运算符(= / != / > / <)")
        v = self.take()
        if v.kind not in ("num", "str", "id"):
            raise _ParseError(v.line, f"过滤条件「{f.text}」缺少取值")

    def group(self) -> None:
        while True:
            d = self.take()
            if d.kind != "id":
                raise _ParseError(d.line, f"GROUP BY 应为维度名称,实际为「{d.text}」")
            if self.all_dims and d.text not in self.all_dims:
                self.semantic(d.line, f"未知维度「{d.text}」")
            elif d.text not in self.dims:
                self.semantic(d.line, f"维度「{d.text}」未在左侧选中")
            elif d.text in self.group_by:
                self.semantic(d.line, f"维度「{d.text}」重复")
            else:
                self.group_by.append(d.text)
            if (tok := self.peek()) is not None and tok.text == ",":
                self.take()
                continue
            break


def validate_formula(req: FormulaRequest) -> FormulaResponse:
    atoms = {a.name: a.cases for a in req.atoms}
    src = req.formula.strip("\n")
    if not src.strip():
        return FormulaResponse(ok=False, name=None, errors=[FormulaError(line=1, message="公式为空")], atoms=[], functions=[],
                               group_by=[], estimated_cases=None, message="✗ 第 1 行:公式为空")
    p: _Parser | None = None
    name: str | None = None
    try:
        p = _Parser(_tokenize(src), atoms, req.dims, req.all_dims, req.filter_fields)
        name = p.parse()
        errors = list(p.errors)
    except _ParseError as e:
        errors = (list(p.errors) if p else []) + [FormulaError(line=e.line, message=e.message)]
    errors.sort(key=lambda e: e.line)
    ok = not errors
    est: int | None = None
    if ok and p is not None:
        base = p.count_atom or p.div_atom or (p.used_atoms[-1] if p.used_atoms else None)
        est = atoms.get(base) if base else None
    if ok:
        parts = ["✓ 语法校验通过"]
        if req.caliber:
            parts.append(f"引用口径:{req.caliber}")
        if est:
            parts.append(f"预计取数 {est:,} 例")
        message = " · ".join(parts)
    else:
        message = f"✗ 第 {errors[0].line} 行:{errors[0].message}" + (f"(另有 {len(errors) - 1} 处)" if len(errors) > 1 else "")
    return FormulaResponse(ok=ok, name=name, errors=errors, atoms=p.used_atoms if p else [], functions=p.used_funcs if p else [],
                           group_by=p.group_by if p else [], estimated_cases=est, message=message)


@router.post("/formula/validate", response_model=FormulaResponse, **OPTS)
def formula_validate(req: FormulaRequest) -> FormulaResponse:
    """公式编辑器校验：语法、原子指标、过滤字段、GROUP BY 维度须已选中；通过时给出预计取数。"""
    return validate_formula(req)


# ================================================================ 以机构身份预览（A4 第 4 步）


class OrgValue(Camel):
    org: str
    value: float
    cases: int = Field(ge=0)


class PeerGroup(Camel):
    name: str
    orgs: list[OrgValue]


class PreviewRequest(Camel):
    #: 预览身份：机构名称；为空 = 召集人全量视图（分析监测区）
    viewer_org: str | None = None
    groups: list[PeerGroup]
    min_orgs: int = Field(default=5, ge=1, le=50)
    min_cases: int = Field(default=30, ge=0, le=100000)
    internal: bool = False


class GroupStat(Camel):
    name: str
    n: int
    cases: int
    suppressed: bool
    p25: float | None = None
    p50: float | None = None
    p75: float | None = None


class PreviewResponse(Camel):
    mode: Literal["normal", "suppressed", "internal", "all"]
    org: str | None = None
    group: str | None = None
    peer_count: int | None = None
    value: float | None = None
    pct: int | None = None
    p25: float | None = None
    p50: float | None = None
    p75: float | None = None
    city_mean: float | None = None
    note: str | None = None
    #: 仅召集人全量视图
    groups: list[GroupStat] | None = None
    total_cases: int | None = None


def _city_mean(groups: list[PeerGroup]) -> float:
    """全市均值：按病例数加权（Σ 值 × 例数 ÷ Σ 例数）。"""
    tot = sum(o.cases for g in groups for o in g.orgs)
    if tot == 0:
        vals = [o.value for g in groups for o in g.orgs]
        return r1(sum(vals) / len(vals)) if vals else 0.0
    return r1(sum(o.value * o.cases for g in groups for o in g.orgs) / tot)


def _group_stat(g: PeerGroup, min_orgs: int, min_cases: int) -> GroupStat:
    vals = [o.value for o in g.orgs]
    cases = sum(o.cases for o in g.orgs)
    if not vals or len(vals) < min_orgs or cases < min_cases:
        return GroupStat(name=g.name, n=len(vals), cases=cases, suppressed=True)
    return GroupStat(name=g.name, n=len(vals), cases=cases, suppressed=False, p25=r1(quantile(vals, 0.25)),
                     p50=r1(quantile(vals, 0.5)), p75=r1(quantile(vals, 0.75)))


def preview(req: PreviewRequest) -> PreviewResponse:
    if req.viewer_org is None:
        stats = [_group_stat(g, req.min_orgs, req.min_cases) for g in req.groups]
        return PreviewResponse(mode="all", groups=stats, city_mean=_city_mean(req.groups), total_cases=sum(s.cases for s in stats))
    # 仅内部：机构身份下不渲染，任何数值都不返回
    if req.internal:
        return PreviewResponse(mode="internal", org=req.viewer_org, note="该指标为“仅内部”:机构身份下不渲染,且不能加入发布包。")
    grp = next((g for g in req.groups if any(o.org == req.viewer_org for o in g.orgs)), None)
    if grp is None:
        return PreviewResponse(mode="suppressed", org=req.viewer_org, note="本期无该机构数据,不输出。", city_mean=_city_mean(req.groups))
    me = next(o for o in grp.orgs if o.org == req.viewer_org)
    n = len(grp.orgs)
    base = dict(org=req.viewer_org, group=grp.name, peer_count=n, value=r1(me.value), city_mean=_city_mean(req.groups))
    if n < req.min_orgs:
        return PreviewResponse(mode="suppressed", **base,
                               note=f"同级组仅 {n} 家,低于抑制阈值 {req.min_orgs} 家:同级分位不输出,仅展示本院值与全市均值。")
    if me.cases < req.min_cases:
        return PreviewResponse(mode="suppressed", **base,
                               note=f"本院本期病例 {me.cases} 例,低于抑制阈值 {req.min_cases} 例:同级分位不输出,仅展示本院值与全市均值。")
    vals = [o.value for o in grp.orgs]
    return PreviewResponse(mode="normal", **base, pct=percentile_rank(vals, me.value), p25=r1(quantile(vals, 0.25)),
                           p50=r1(quantile(vals, 0.5)), p75=r1(quantile(vals, 0.75)), note="不显示其他机构名称与数值。")


@router.post("/preview", response_model=PreviewResponse, response_model_exclude_none=True, **OPTS)
def preview_route(req: PreviewRequest) -> PreviewResponse:
    """以机构身份预览：机构只见本院值与同级 P25 / P50 / P75；小样本抑制；仅内部不返回数值。"""
    return preview(req)


# ================================================================ 选题推荐（A6）

#: 综合得分权重（工作组年度复核）：病例量、结算金额、趋势、基金差额、机构关注度、机构推荐、收治结构 Z 分数
FACTORS: list[tuple[str, float, str]] = [
    ("caseVolume", 0.20, "病例量大"),
    ("amount", 0.15, "结算金额大"),
    ("trend", 0.15, "趋势变化大"),
    ("fundGap", 0.20, "基金差额大"),
    ("attention", 0.10, "机构关注度高"),
    ("recommend", 0.10, "机构推荐集中"),
    ("admitZ", 0.10, "收治结构Z分数异常"),
]


class TopicFactors(Camel):
    case_volume: float = Field(ge=0, le=100)
    amount: float = Field(ge=0, le=100)
    trend: float = Field(ge=0, le=100)
    fund_gap: float = Field(ge=0, le=100)
    attention: float = Field(ge=0, le=100)
    recommend: float = Field(ge=0, le=100)
    admit_z: float = Field(ge=0, le=100)


class TopicIn(Camel):
    code: str
    name: str
    factors: TopicFactors


class TopicScoreRequest(Camel):
    rows: list[TopicIn]
    #: 分项得分 ≥ 该值记为入选理由
    reason_threshold: float = 90


class TopicScore(Camel):
    code: str
    name: str
    score: int
    reasons: list[str]


class TopicScoreResponse(Camel):
    rows: list[TopicScore]
    weights: dict[str, float]


def topic_scores(req: TopicScoreRequest) -> TopicScoreResponse:
    out = []
    for t in req.rows:
        f = t.factors.model_dump(by_alias=True)
        score = sum(f[k] * w for k, w, _ in FACTORS)
        hits = [(f[k], i, label) for i, (k, _, label) in enumerate(FACTORS) if f[k] >= req.reason_threshold]
        hits.sort(key=lambda x: (-x[0], x[1]))
        out.append(TopicScore(code=t.code, name=t.name, score=int(score + 0.5), reasons=[h[2] for h in hits]))
    out.sort(key=lambda x: -x.score)
    return TopicScoreResponse(rows=out, weights={label: w for _, w, label in FACTORS})


@router.post("/topic-scores", response_model=TopicScoreResponse, **OPTS)
def topic_scores_route(req: TopicScoreRequest) -> TopicScoreResponse:
    """选题综合得分 = 七项分项得分加权；分项 ≥ 阈值的记为入选理由（按分项得分降序）。"""
    return topic_scores(req)


# ================================================================ 归因推荐（A6）


class AttrBehavior(Camel):
    name: str
    amount: float


class AttrIn(Camel):
    code: str
    name: str
    patient_diff: float
    behavior_diff: float
    behaviors: list[AttrBehavior] = []
    comorbidity_pct: float = Field(ge=0, le=100)
    cases: int = Field(ge=0)
    #: 该组结算清单质控率最低的机构（用于说明暂不输出的原因）
    low_qc_org: str | None = None
    low_qc_pct: float | None = None


class AttributionRequest(Camel):
    rows: list[AttrIn]
    min_comorbidity_pct: float = 60
    min_cases: int = 50


class AttrOut(Camel):
    code: str
    name: str
    status: Literal["ok", "withheld"]
    total_diff: float | None = None
    patient_pct: float | None = None
    behavior_pct: float | None = None
    top_behaviors: list[AttrBehavior] = []
    reason: str | None = None


class AttributionResponse(Camel):
    rows: list[AttrOut]


def attribution(req: AttributionRequest) -> AttributionResponse:
    out = []
    for a in req.rows:
        why = []
        if a.comorbidity_pct < req.min_comorbidity_pct:
            why.append(f"本组合并症编码率 {a.comorbidity_pct:g}%,低于 {req.min_comorbidity_pct:g}% 的输出门槛")
        if a.cases < req.min_cases:
            why.append(f"本组病例 {a.cases} 例,低于 {req.min_cases} 例的输出门槛")
        if why:
            if a.low_qc_org and a.low_qc_pct is not None:
                why.append(f"{a.low_qc_org}结算清单质控率 {a.low_qc_pct:g}%")
            out.append(AttrOut(code=a.code, name=a.name, status="withheld", reason="原因:" + ";".join(why) + "。编码质量改善后自动重算。"))
            continue
        total = a.patient_diff + a.behavior_diff
        pp = r1(a.patient_diff / total * 100) if total else 0.0
        top = sorted(a.behaviors, key=lambda b: -b.amount)[:2]
        out.append(AttrOut(code=a.code, name=a.name, status="ok", total_diff=round(total), patient_pct=pp, behavior_pct=r1(100 - pp),
                           top_behaviors=top))
    return AttributionResponse(rows=out)


@router.post("/attribution", response_model=AttributionResponse, response_model_exclude_none=True, **OPTS)
def attribution_route(req: AttributionRequest) -> AttributionResponse:
    """机构间例均费用差异拆分为患者差异 / 行为差异；合并症编码率或病例数不达标时暂不输出并说明原因。"""
    return attribution(req)


# ================================================================ 标杆推荐（A6）


class BenchOrg(Camel):
    avg_cost: float = Field(gt=0)
    qc_pct: float = Field(ge=0, le=100)


class BenchGroup(Camel):
    name: str
    orgs: list[BenchOrg]


class BenchmarkRequest(Camel):
    #: 偏离阈值 P60–P90，步长 5
    threshold: int = Field(ge=60, le=90, multiple_of=5)
    groups: list[BenchGroup]
    min_orgs: int = 5
    bench_pct: int = 25
    min_qc_pct: float = 95


class BenchRow(Camel):
    name: str
    n: int
    small: bool
    bench: int | None = None
    middle: int | None = None
    deviant: int | None = None
    bench_line: float | None = None
    deviant_line: float | None = None


class BenchmarkResponse(Camel):
    threshold: int
    rows: list[BenchRow]
    rule: str


def benchmark(req: BenchmarkRequest) -> BenchmarkResponse:
    rows = []
    for g in req.groups:
        n = len(g.orgs)
        if n < req.min_orgs:
            rows.append(BenchRow(name=g.name, n=n, small=True))
            continue
        costs = [o.avg_cost for o in g.orgs]
        lo = quantile(costs, req.bench_pct / 100)
        hi = quantile(costs, req.threshold / 100)
        dev = sum(1 for o in g.orgs if o.avg_cost > hi)
        ben = sum(1 for o in g.orgs if o.avg_cost <= lo and o.qc_pct >= req.min_qc_pct and not o.avg_cost > hi)
        rows.append(BenchRow(name=g.name, n=n, small=False, bench=ben, middle=n - ben - dev, deviant=dev, bench_line=round(lo),
                             deviant_line=round(hi)))
    rule = (f"标杆组 = 例均费用 ≤ P{req.bench_pct} 且 结算清单质控率 ≥ {req.min_qc_pct:g}%;"
            f"偏离组 = 例均费用 > P{req.threshold}")
    return BenchmarkResponse(threshold=req.threshold, rows=rows, rule=rule)


@router.post("/benchmark", response_model=BenchmarkResponse, response_model_exclude_none=True, **OPTS)
def benchmark_route(req: BenchmarkRequest) -> BenchmarkResponse:
    """标杆 / 中间 / 偏离分组：同级机构数 < 5 的组样本不足，不分组。"""
    return benchmark(req)
