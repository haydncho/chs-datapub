/**
 * 全景图 (cockpit) — demo seed. Two viewer identities share the city-wide
 * reference tables (DRG groups, institutions, flows, publication matrix) and
 * each carry their own KPIs, panels and loop progress.
 * Backend: GET /api/v1/pages/cockpit returns the same `CockpitData` shape.
 *
 * 周期口径(月 / 季 / 年)全部由同一份逐月数据(2025-01 … 2026-08)推出,保证同一指标在
 * 指标卡、走势线、「钱」柱与读数里是同一组数:
 *   月 = 2026年8月(较上月);季 = 2026年Q2,最近一个完整季度(较上季);
 *   年 = 2026年1–8月累计(较去年同期)。金额类按期内合计,率 / 指数 / 例均类按期内月均,
 *   时点类(编码不达标机构数等)取期末值。病组 / 机构 / 科室的期内数据见各行的 byPeriod。
 */

export type IdentityId = 'conv' | 'hosp'
export type CockpitView = 'bub' | 'inst' | 'flow' | 'pub' | 'dept' | 'peer'
export type Period = '月' | '季' | '年'
/** 1 = higher is better, -1 = lower is better, 0 = neutral (no good/bad colouring) */
export type GoodDir = 1 | -1 | 0
export type Tone = 'ok' | 'warn' | 'bad'
export type LoopStatus = 'ok' | 'warn' | 'act'
/** publication matrix cell: ok 已公开(查阅率) · low 查阅率低 · np 未公开 · cmt 意见未复 · int 仅内部 · na 不适用 */
export type MatrixStatus = 'ok' | 'low' | 'np' | 'cmt' | 'int' | 'na'

export interface CockpitKpi {
  label: string; value: string; unit: string
  /** 较上期变化(月:较上月;季:较上季;年:较去年同期),单位见 deltaUnit */
  delta: number; goodDir: GoodDir
  /** '%' = 变化率;缺省 = 与 value 同单位的差值 */
  deltaUnit?: string
  /** 本周期口径下的走势(最后一个点就是 value),与「钱」柱同源 */
  trend?: number[]
}
export interface CockpitEff {
  label: string; value: number
  /** 1 = 越高越好(CMI:不设「偏高」警示);缺省 / 0 = 目标带 0.95–1.05(消耗指数) */
  goodDir?: GoodDir
}
export interface CockpitErr {
  label: string; value: string; unit: string; tone: Tone
  /** 较上期变化与"好"的方向(同指标卡);缺省时不显示环比 */
  delta?: number; goodDir?: GoodDir
}
export interface CockpitAlert {
  type: string; text: string; date: string
  /** 服务端下发:该告警是否已被确认处置(cockpit/ackAlarm)。缺省 = 无服务端状态(离线演示) */
  acked?: boolean
}
export interface CockpitLoopStep { name: string; value: string; pct: number; sub: string; status: LoopStatus }

/** 「钱」面板的柱:values 为柱值,line 为每根柱对应的参照线(医保局:预算;本院:DRG 支付) */
export interface CockpitBars {
  labels: string[]
  values: number[]
  line: number[]
  /** 跨年分隔所在的柱下标(-1 = 无)及年份 */
  yearAt: number
  year: string
  /** true = 逐月累计(年口径),最后一根柱即期内合计 */
  cumulative?: boolean
}
export interface CockpitMoney {
  title: string
  unit: string
  m1: string; budget: string
  m2: string; spend: string
  m3: string; balance: string
  legendBar: string
  legendLine: string
  bars: CockpitBars
}
/** 一个周期口径下的指标卡与右栏数据 */
export interface CockpitPeriodView { kpis: CockpitKpi[]; money: CockpitMoney; eff: CockpitEff[]; errs: CockpitErr[] }

/** 已保存的订阅(服务端 cockpit_subscription,按登录用户 × 视角) */
export interface CockpitSavedSubscription {
  enabled: boolean
  frequency: string
  contents: string[]
  channel: string
  recipients: string[]
  updatedAt?: string
}

export interface CockpitIdentity extends CockpitPeriodView {
  id: IdentityId
  /** identity tab label */
  tab: string
  /** person (header / watermark) */
  name: string
  /** short organisation (tab subtitle / watermark) */
  orgShort: string
  role: string
  scope: string
  /** full organisation name on the big screen */
  org: string
  zone: string
  title: string
  views: CockpitView[]
  /** 季 / 年 口径(月口径即本对象顶层的 kpis / money / eff / errs) */
  periods: { 季: CockpitPeriodView; 年: CockpitPeriodView }
  tornTitle: string
  effTitle: string
  errTitle: string
  alertTitle: string
  alerts: CockpitAlert[]
  loopTitle: string
  loop: CockpitLoopStep[]
  flowNote: string
  /** bubble x axis label */
  xLabel: string
  /** secondary screen titles */
  screen2Title: string
  screen2Right: string
  /** alarm banner subtitle */
  alarmSub: string
  /** subscription recipients */
  recipients: string[]
  /** subscription preview: 待办 line */
  todo: string
  /** 本院视角:本院病组(气泡 / 超支与结余);医保局视角无 */
  ownDrgs?: CockpitOwnDrg[]
  /** 当前登录用户在该视角下已保存的订阅;null / 缺省 = 未开启 */
  savedSubscription?: CockpitSavedSubscription | null
}

export interface CockpitDrgPeriod { cases: number; diff: number }
export interface CockpitDrg { code: string; name: string; cases: number; diff: number; cost: number; byPeriod?: Partial<Record<Period, CockpitDrgPeriod>> }
export interface CockpitInstitution { name: string; district: string; tier: number; diff: number; byPeriod?: Partial<Record<Period, { diff: number }>> }
export interface CockpitFlow { name: string; scope: '省内' | '省外' | '—'; share: number; amount: string }
export interface CockpitMatrixCell { status: MatrixStatus; value?: number }
export interface CockpitMatrixRow { name: string; cells: CockpitMatrixCell[]; internal?: boolean }
export interface CockpitDept { name: string; cases: number; diff: number; cmi: number; byPeriod?: Partial<Record<Period, { cases: number; diff: number; cmi: number }>> }
/**
 * 同级对标。pct 与 B1 同口径:**按表现排位,越高越好**(P100 = 同级最优;越低越好的指标数值低则分位高),
 * 分位 > 50 即优于同级中位。higherIsBetter 只说明指标数值的方向(画分布点时「较好」一侧)。
 * 机构端由 HospitalCockpitScope 匿名化:own / others / ownIndex,values 为 others 中按 ownIndex 插入 own。
 */
export interface CockpitPeer {
  name: string; values: number[]; higherIsBetter: boolean; value: string; pct: string
  own?: number; others?: number[]; ownIndex?: number
}
/** 本院病组(与 B1 病组表同源):本院病例数、例均基金差额、次均费用 */
export interface CockpitOwnDrg { code: string; cases: number; diff: number; cost: number; byPeriod?: Partial<Record<Period, CockpitDrgPeriod>> }

export interface CockpitData {
  identities: CockpitIdentity[]
  drgs: CockpitDrg[]
  /** DRG codes ringed under the 错 / 效 lenses */
  errFlag: string[]
  effFlag: string[]
  districts: string[]
  tiers: string[]
  institutions: CockpitInstitution[]
  /** 参保地 node of the 异地流向 view */
  flowOrigin: { name: string; sub: string }
  flows: CockpitFlow[]
  audiences: string[]
  /** 由 matrix 按格计数(前端同样从 matrix 现算) */
  matrixSummary: { unpublished: number; unread: number; unanswered: number }
  matrix: CockpitMatrixRow[]
  depts: CockpitDept[]
  peers: CockpitPeer[]
  /** 由 peers 推出(前端同样从 peers 现算),better = 优于同级中位的项数 */
  peerSummary: { better: string; watch: string; cmiPct: string; diffPct: string }
  subscription: { frequencies: string[]; whenLabels: string[]; contents: string[]; channels: string[] }
  dataAsOf: string
  periodLabels: Record<Period, string>
  /** 各周期包含的月数(病例数、差额总额按此折算颜色阈值) */
  periodMonths: Record<Period, number>
  /** 机构端:登录机构不是示例数据所属医院时为 true,本院数据均已置空(见 HospitalCockpitScope) */
  noOwnData?: boolean
  noOwnDataNote?: string
}

/* ====================================================================== 逐月数据 → 周期口径 */

/** 逐月序列下标:0 = 2025-01 … 19 = 2026-08 */
const N = 20
const MONTH_OF = (i: number) => (i % 12) + 1
const range = (a: number, b: number) => Array.from({ length: b - a }, (_, i) => a + i)
/** 月:近 12 月 2025-09 … 2026-08 */
const LAST12 = range(N - 12, N)
/** 季:2025Q1 … 2026Q2(6 个完整季度) */
const QUARTERS = range(0, 6).map(q => range(q * 3, q * 3 + 3))
/** 年:2026 年 1–8 月,与 2025 年 1–8 月同比 */
const YTD = range(12, N)
const YTD_PREV = range(0, 8)

const r1 = (v: number) => Math.round(v * 10) / 10
const roundTo = (v: number, d: number) => Math.round(v * 10 ** d) / 10 ** d
const sum = (xs: number[]) => xs.reduce((a, x) => a + x, 0)
const thousands = (n: number) => String(Math.round(Math.abs(n))).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
const signed = (n: number) => (n > 0 ? '+' : n < 0 ? '−' : '') + thousands(n)

/** 一个指标:按下标集合取值(合计 / 均值 / 期末 / 比率) */
interface Metric {
  get: (idx: number[]) => number
  digits: number
  fmt?: (v: number) => string
}
const S = (series: number[], digits: number, fmt?: (v: number) => string): Metric =>
  ({ get: idx => sum(idx.map(i => series[i]!)), digits, fmt })
const M = (series: number[], digits: number, fmt?: (v: number) => string): Metric =>
  ({ get: idx => sum(idx.map(i => series[i]!)) / idx.length, digits, fmt })
const L = (series: number[], digits: number): Metric => ({ get: idx => series[idx[idx.length - 1]!]!, digits })

/** 本期值 / 上期值 / 走势(均已按显示精度取整,保证 delta 与看到的数字一致) */
function slice(m: Metric, p: Period) {
  const v = (idx: number[]) => roundTo(m.get(idx), m.digits)
  if (p === '月') return { cur: v([N - 1]), prev: v([N - 2]), trend: LAST12.map(i => v([i])) }
  if (p === '季') return { cur: v(QUARTERS[5]!), prev: v(QUARTERS[4]!), trend: QUARTERS.map(v) }
  return { cur: v(YTD), prev: v(YTD_PREV), trend: YTD.map((_, j) => v(YTD.slice(0, j + 1))) }
}
const show = (m: Metric, v: number) => (m.fmt ? m.fmt(v) : v.toFixed(m.digits))

function kpi(label: string, unit: string, m: Metric, p: Period, goodDir: GoodDir, rate = false): CockpitKpi {
  const { cur, prev, trend } = slice(m, p)
  const delta = rate ? r1(((cur - prev) / prev) * 100) : roundTo(cur - prev, m.digits)
  return { label, value: show(m, cur), unit, delta, goodDir, ...(rate ? { deltaUnit: '%' } : {}), trend }
}
function err(label: string, unit: string, m: Metric, p: Period, tone: (v: number) => Tone, goodDir: GoodDir): CockpitErr {
  const { cur, prev } = slice(m, p)
  return { label, value: show(m, cur), unit, tone: tone(cur), delta: roundTo(cur - prev, m.digits), goodDir }
}
const eff = (label: string, m: Metric, p: Period, goodDir?: GoodDir): CockpitEff =>
  ({ label, value: slice(m, p).cur, ...(goodDir ? { goodDir } : {}) })

/** 「钱」柱:与指标卡同源 */
function bars(values: Metric, line: Metric, p: Period): CockpitBars {
  if (p === '月') {
    return {
      labels: LAST12.map(i => String(MONTH_OF(i))), values: LAST12.map(i => roundTo(values.get([i]), values.digits)),
      line: LAST12.map(i => roundTo(line.get([i]), 2)), yearAt: LAST12.indexOf(12), year: '2026',
    }
  }
  if (p === '季') {
    return {
      labels: ['25Q1', '25Q2', '25Q3', '25Q4', '26Q1', '26Q2'], values: QUARTERS.map(q => roundTo(values.get(q), values.digits)),
      line: QUARTERS.map(q => roundTo(line.get(q), 2)), yearAt: -1, year: '',
    }
  }
  return {
    labels: YTD.map(i => String(MONTH_OF(i))), values: YTD.map((_, j) => roundTo(values.get(YTD.slice(0, j + 1)), values.digits)),
    line: YTD.map((_, j) => roundTo(line.get(YTD.slice(0, j + 1)), 2)), yearAt: -1, year: '', cumulative: true,
  }
}
const PERIOD_WORD: Record<Period, { cur: string; unit: string }> = {
  月: { cur: '本月', unit: '近 12 月' },
  季: { cur: '本季', unit: '近 6 季' },
  年: { cur: '累计', unit: '1–8 月逐月累计' },
}

/* ---------------------------------------------------------------- 医保局(全市) */
const C = {
  /** 统筹基金支出 亿元(合计) */
  spend: [3.18, 3.05, 3.30, 3.27, 3.36, 3.31, 3.44, 3.40, 3.42, 3.51, 3.38, 3.60, 3.55, 3.71, 3.64, 3.78, 3.69, 3.82, 3.74, 3.86],
  /** 月度预算 亿元 */
  budget: Array.from({ length: N }, () => 3.95),
  balanceRate: [3.2, 3.4, 3.3, 3.5, 3.4, 3.6, 3.5, 3.7, 3.6, 3.8, 3.9, 3.7, 3.8, 3.9, 4.0, 3.9, 4.1, 4.0, 3.8, 4.2],
  diff: [12, 6, 8, 2, -4, 0, -6, -8, -10, -14, -6, -18, -22, -15, -20, -26, -30, -24, -26, -38],
  cmi: [0.98, 0.99, 0.99, 1.0, 1.0, 0.99, 1.01, 1.0, 1.01, 1.02, 1.0, 1.02, 1.03, 1.02, 1.03, 1.04, 1.03, 1.04, 1.02, 1.04],
  qc: [93.6, 93.9, 94.0, 94.2, 94.1, 94.5, 94.4, 94.6, 94.8, 95.0, 95.3, 95.1, 95.6, 95.4, 95.8, 95.9, 96.0, 95.7, 95.6, 96.4],
  signRate: [84, 85, 87, 86, 88, 87, 89, 90, 90, 91, 93, 92, 94, 93, 95, 94, 96, 95, 95, 97],
  costIdx: [1.01, 1.0, 1.0, 0.99, 1.0, 0.99, 0.99, 0.98, 0.99, 0.98, 0.99, 0.98, 0.98, 0.97, 0.98, 0.98, 0.97, 0.98, 0.99, 0.98],
  timeIdx: [1.06, 1.05, 1.05, 1.04, 1.05, 1.04, 1.04, 1.04, 1.03, 1.04, 1.03, 1.04, 1.03, 1.03, 1.02, 1.03, 1.03, 1.02, 1.04, 1.03],
  deduct: [150.2, 141.6, 158.3, 162.0, 155.4, 166.8, 170.1, 164.5, 168.2, 171.9, 160.4, 175.3, 169.0, 172.6, 178.1, 180.4, 176.2, 182.0, 173.8, 186.4],
  upcode: [55, 51, 58, 54, 52, 50, 49, 53, 51, 47, 50, 52, 46, 49, 45, 47, 44, 46, 48, 42],
  coding: [6, 6, 5, 5, 5, 4, 4, 4, 4, 4, 3, 3, 3, 3, 2, 2, 2, 3, 2, 3],
  overdue: [1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0],
}
const CM = {
  spend: S(C.spend, 2),
  budget: S(C.budget, 2),
  balanceRate: M(C.balanceRate, 1),
  diff: M(C.diff, 0, signed),
  cmi: M(C.cmi, 2),
  qc: M(C.qc, 1),
  signRate: M(C.signRate, 0),
  costIdx: M(C.costIdx, 2),
  timeIdx: M(C.timeIdx, 2),
  deduct: S(C.deduct, 1),
  upcode: S(C.upcode, 0),
  coding: L(C.coding, 0),
  overdue: L(C.overdue, 0),
}
function convView(p: Period): CockpitPeriodView {
  const w = PERIOD_WORD[p]
  const spend = slice(CM.spend, p).cur
  const budget = slice(CM.budget, p).cur
  return {
    kpis: [
      kpi('统筹基金支出', '亿', CM.spend, p, 1, true),
      kpi('当期结余率', '%', CM.balanceRate, p, 1),
      kpi('例均基金差额', '元', CM.diff, p, -1),
      kpi('CMI', '', CM.cmi, p, 1),
      kpi('清单质控率', '%', CM.qc, p, 1),
      kpi('本期签收率', '%', CM.signRate, p, 1),
    ],
    money: {
      title: '钱 · 基金收支', unit: '亿元 · ' + w.unit,
      m1: '预算执行率', budget: ((spend / budget) * 100).toFixed(1) + '%',
      m2: w.cur + '支出', spend: spend.toFixed(2),
      m3: '当期结余率', balance: slice(CM.balanceRate, p).cur.toFixed(1) + '%',
      legendBar: '支出', legendLine: '预算',
      bars: bars(CM.spend, CM.budget, p),
    },
    eff: [eff('CMI', CM.cmi, p, 1), eff('费用消耗指数', CM.costIdx, p), eff('时间消耗指数', CM.timeIdx, p)],
    errs: [
      err('审核扣款', '万', CM.deduct, p, () => 'warn', -1),
      err('疑似高套', '例', CM.upcode, p, () => 'bad', -1),
      err('编码不达标', '家', CM.coding, p, v => (v > 0 ? 'warn' : 'ok'), -1),
      err('逾期统筹', '个', CM.overdue, p, v => (v > 0 ? 'warn' : 'ok'), -1),
    ],
  }
}

/* ---------------------------------------------------------------- 本院(示例市第一人民医院) */
/** 医保记账 万元;2026-08 = 4,862.4(与 B1 / B4 / D1 一致) */
const H_RECORDED = [4310, 4180, 4420, 4390, 4460, 4430, 4550, 4490, 4520, 4610, 4470, 4700, 4650, 4800, 4720, 4890, 4780, 4940, 4820, 4862.4]
/** DRG 支付 / 医保记账 %;2026-08 由 4,615.7 / 4,862.4 得出,7 月 94.5%(D1:「较上月收窄 0.4 个百分点」) */
const H_RATIO = [96.4, 96.3, 96.0, 96.5, 96.2, 95.9, 96.1, 95.8, 96.1, 96.0, 95.8, 96.2, 95.5, 95.7, 95.4, 95.6, 95.2, 95.5, 94.5]
const H_PAID = [...H_RATIO.map((r, i) => r1((H_RECORDED[i]! * r) / 100)), 4615.7]
const H = {
  diff: [330, 342, 350, 338, 356, 362, 370, 365, 380, 395, 410, 402, 430, 425, 440, 452, 448, 460, 448, 486],
  cmi: [1.05, 1.06, 1.05, 1.06, 1.07, 1.06, 1.07, 1.08, 1.08, 1.09, 1.08, 1.1, 1.09, 1.1, 1.11, 1.1, 1.11, 1.12, 1.09, 1.12],
  grouping: [97.8, 98.0, 98.1, 98.0, 98.3, 98.2, 98.4, 98.5, 98.5, 98.7, 98.6, 98.9, 98.8, 99.0, 98.9, 99.1, 99.0, 99.2, 98.9, 99.2],
  deduct: [16.8, 15.9, 17.2, 16.4, 15.8, 16.6, 15.1, 15.5, 14.2, 15.0, 13.8, 16.1, 15.2, 14.6, 13.9, 14.8, 13.5, 14.1, 15.0, 12.6],
  refused: [31, 28, 30, 27, 26, 29, 25, 24, 26, 22, 24, 21, 23, 20, 22, 18, 21, 20, 19, 23],
  qc: [94.0, 94.2, 94.5, 94.3, 94.8, 94.6, 95.0, 94.9, 95.1, 95.3, 95.0, 95.4, 95.2, 95.6, 95.5, 95.8, 95.7, 95.9, 95.6, 96.4],
  highRatio: [11, 10, 12, 9, 10, 11, 9, 10, 9, 8, 10, 9, 8, 9, 8, 7, 9, 8, 9, 7],
  costIdx: [1.08, 1.07, 1.07, 1.06, 1.07, 1.06, 1.06, 1.05, 1.06, 1.05, 1.05, 1.06, 1.05, 1.04, 1.05, 1.04, 1.05, 1.04, 1.05, 1.04],
  timeIdx: [1.02, 1.01, 1.01, 1.0, 1.0, 0.99, 1.0, 0.99, 0.99, 0.98, 0.99, 0.98, 0.98, 0.97, 0.98, 0.97, 0.98, 0.97, 0.98, 0.97],
}
const HM = {
  recorded: S(H_RECORDED, 1),
  paid: S(H_PAID, 1, thousands),
  ratio: { get: (idx: number[]) => (sum(idx.map(i => H_PAID[i]!)) / sum(idx.map(i => H_RECORDED[i]!))) * 100, digits: 1 } as Metric,
  diff: M(H.diff, 0, signed),
  cmi: M(H.cmi, 2),
  grouping: M(H.grouping, 1),
  deduct: S(H.deduct, 1),
  refused: S(H.refused, 0),
  qc: M(H.qc, 1),
  highRatio: S(H.highRatio, 0),
  costIdx: M(H.costIdx, 2),
  timeIdx: M(H.timeIdx, 2),
}
function hospView(p: Period): CockpitPeriodView {
  const w = PERIOD_WORD[p]
  const rec = slice(HM.recorded, p).cur
  const paid = roundTo(HM.paid.get(p === '月' ? [N - 1] : p === '季' ? QUARTERS[5]! : YTD), 1)
  const gap = r1(paid - rec)
  return {
    kpis: [
      kpi('DRG 支付', '万', HM.paid, p, 0, true),
      kpi('支付 / 记账', '%', HM.ratio, p, 1),
      kpi('例均基金差额', '元', HM.diff, p, -1),
      kpi('CMI', '', HM.cmi, p, 1),
      kpi('DRG 入组率', '%', HM.grouping, p, 1),
      kpi('审核扣款', '万', HM.deduct, p, -1),
    ],
    money: {
      title: '钱 · 本院医保结算', unit: '万元 · ' + w.unit,
      m1: '支付 / 记账', budget: slice(HM.ratio, p).cur.toFixed(1) + '%',
      m2: w.cur + '医保记账', spend: thousands(rec) + '万',
      m3: w.cur + '结算盈亏', balance: (gap < 0 ? '−' : '+') + thousands(Math.trunc(gap)) + '.' + Math.round(Math.abs(gap * 10)) % 10 + '万',
      legendBar: '医保记账', legendLine: 'DRG 支付',
      bars: bars(HM.recorded, HM.paid, p),
    },
    eff: [
      eff(p === '月' ? 'CMI · 同级 P68' : 'CMI', HM.cmi, p, 1),
      eff('费用消耗指数', HM.costIdx, p),
      eff('时间消耗指数', HM.timeIdx, p),
    ],
    errs: [
      err('审核扣款', '万', HM.deduct, p, () => 'warn', -1),
      err('拒付病例', '例', HM.refused, p, () => 'bad', -1),
      err('清单质控率', '%', HM.qc, p, () => 'ok', 1),
      err('高倍率病例', '例', HM.highRatio, p, () => 'warn', -1),
    ],
  }
}

/* ---------------------------------------------------------------- 病组 / 机构 / 科室的期内数据 */
/** 稳定的小扰动(按编码),让季 / 年的例均差额与病例数不是简单的倍数 */
const jit = (s: string, mod: number) => {
  let x = 7
  for (const c of s) x = (x * 31 + c.charCodeAt(0)) % 9973
  return x % mod
}
const ten = (v: number) => Math.round(v / 10) * 10
function drgPeriods(d: Omit<CockpitDrg, 'byPeriod'>): CockpitDrg {
  return {
    ...d,
    byPeriod: {
      季: { cases: Math.round(d.cases * 3 * (0.93 + jit(d.code, 9) / 100)), diff: ten(d.diff * (0.9 + jit(d.code + 'q', 17) / 100)) },
      年: { cases: Math.round(d.cases * 8 * (0.9 + jit(d.code, 11) / 100)), diff: ten(d.diff * (0.88 + jit(d.code + 'y', 15) / 100)) },
    },
  }
}
function instPeriods(x: Omit<CockpitInstitution, 'byPeriod'>): CockpitInstitution {
  return {
    ...x,
    byPeriod: {
      季: { diff: Math.round(x.diff * (0.9 + jit(x.name + 'q', 17) / 100)) },
      年: { diff: Math.round(x.diff * (0.88 + jit(x.name + 'y', 15) / 100)) },
    },
  }
}
function deptPeriods(x: Omit<CockpitDept, 'byPeriod'>): CockpitDept {
  return {
    ...x,
    byPeriod: {
      季: { cases: Math.round(x.cases * 3 * (0.94 + jit(x.name, 8) / 100)), diff: ten(x.diff * (0.9 + jit(x.name + 'q', 17) / 100)), cmi: roundTo(x.cmi - 0.01 + jit(x.name, 3) / 100, 2) },
      年: { cases: Math.round(x.cases * 8 * (0.9 + jit(x.name, 11) / 100)), diff: ten(x.diff * (0.88 + jit(x.name + 'y', 15) / 100)), cmi: roundTo(x.cmi - 0.02 + jit(x.name, 3) / 100, 2) },
    },
  }
}

/* ---------------------------------------------------------------- 同级对标(与 B1 同一组分位) */
const PEERS: CockpitPeer[] = [
  { name: 'CMI 值', values: [1.31, 1.18, 1.12, 1.06, 0.98, 0.91], higherIsBetter: true, value: '1.12', pct: 'P68' },
  { name: '费用消耗指数', values: [0.92, 0.97, 1.04, 1.01, 1.08, 1.12], higherIsBetter: false, value: '1.04', pct: 'P42' },
  { name: '时间消耗指数', values: [0.94, 0.99, 0.97, 1.0, 1.03, 1.06], higherIsBetter: false, value: '0.97', pct: 'P58' },
  { name: '次均费用', values: [11800, 12400, 12860, 13400, 14100, 15200], higherIsBetter: false, value: '12,860', pct: 'P55' },
  { name: '医保外费用占比', values: [3.1, 4.2, 6.8, 5.0, 5.6, 7.4], higherIsBetter: false, value: '6.8%', pct: 'P28' },
  { name: '结算清单质控率', values: [98.3, 97.4, 96.4, 96.2, 95.1, 94.0], higherIsBetter: true, value: '96.4%', pct: 'P55' },
  { name: '例均基金差额', values: [-120, 80, 486, 210, 640, 880], higherIsBetter: false, value: '+486', pct: 'P45' },
]
/** 分位 > 50 即优于同级中位(分位已按表现排位,越高越好) */
const pctOf = (p: CockpitPeer) => +p.pct.slice(1)
const PEER_SUMMARY = {
  better: `${PEERS.filter(p => pctOf(p) > 50).length} / ${PEERS.length} 项`,
  watch: [...PEERS].sort((a, b) => pctOf(a) - pctOf(b))[0]!.name,
  cmiPct: PEERS[0]!.pct,
  diffPct: PEERS[6]!.pct,
}

/* ---------------------------------------------------------------- 本院病组(与 B1 病组表同一份数据) */
const OWN_DRGS: Omit<CockpitOwnDrg, 'byPeriod'>[] = [
  { code: 'BR25', cases: 286, diff: 2140, cost: 14820 },
  { code: 'ES35', cases: 312, diff: 180, cost: 9640 },
  { code: 'RE19', cases: 250, diff: 310, cost: 7630 },
  { code: 'EX25', cases: 218, diff: 420, cost: 11800 },
  { code: 'FR45', cases: 318, diff: 180, cost: 8100 },
  { code: 'GG19', cases: 216, diff: 1480, cost: 7900 },
  { code: 'OB29', cases: 260, diff: -195, cost: 4240 },
  { code: 'IU29', cases: 168, diff: 1640, cost: 11020 },
  { code: 'KS15', cases: 210, diff: 460, cost: 8540 },
  { code: 'OF19', cases: 152, diff: 210, cost: 9370 },
  { code: 'FT35', cases: 171, diff: 300, cost: 5590 },
  { code: 'GZ15', cases: 125, diff: 520, cost: 6530 },
  { code: 'FM19', cases: 157, diff: -620, cost: 40130 },
  { code: 'GU25', cases: 161, diff: 240, cost: 4980 },
  { code: 'LR15', cases: 115, diff: 980, cost: 13570 },
  { code: 'HS25', cases: 85, diff: 1210, cost: 14480 },
  { code: 'IC29', cases: 69, diff: 4100, cost: 63420 },
  { code: 'BR11', cases: 66, diff: 3400, cost: 48450 },
]
function ownDrgPeriods(d: Omit<CockpitOwnDrg, 'byPeriod'>): CockpitOwnDrg {
  return {
    ...d,
    byPeriod: {
      季: { cases: Math.round(d.cases * 3 * (0.93 + jit(d.code + 'h', 9) / 100)), diff: ten(d.diff * (0.9 + jit(d.code + 'hq', 17) / 100)) },
      年: { cases: Math.round(d.cases * 8 * (0.9 + jit(d.code + 'h', 11) / 100)), diff: ten(d.diff * (0.88 + jit(d.code + 'hy', 15) / 100)) },
    },
  }
}

const MATRIX: CockpitMatrixRow[] = [
  { name: '例均基金差额', cells: [{ status: 'ok', value: 94 }, { status: 'ok', value: 88 }, { status: 'ok', value: 71 }, { status: 'low', value: 19 }, { status: 'ok', value: 92 }, { status: 'ok', value: 100 }, { status: 'ok', value: 83 }] },
  { name: '次均总费用分位', cells: [{ status: 'ok', value: 91 }, { status: 'ok', value: 84 }, { status: 'np' }, { status: 'np' }, { status: 'ok', value: 90 }, { status: 'ok', value: 100 }, { status: 'ok', value: 80 }] },
  { name: '医保外费用占比', cells: [{ status: 'cmt', value: 6 }, { status: 'ok', value: 76 }, { status: 'ok', value: 62 }, { status: 'na' }, { status: 'ok', value: 88 }, { status: 'ok', value: 100 }, { status: 'np' }] },
  { name: 'CMI值', cells: [{ status: 'ok', value: 96 }, { status: 'ok', value: 89 }, { status: 'ok', value: 70 }, { status: 'na' }, { status: 'ok', value: 93 }, { status: 'ok', value: 100 }, { status: 'ok', value: 85 }] },
  { name: '费用/时间消耗指数', cells: [{ status: 'ok', value: 90 }, { status: 'low', value: 22 }, { status: 'low', value: 14 }, { status: 'na' }, { status: 'ok', value: 86 }, { status: 'ok', value: 100 }, { status: 'ok', value: 78 }] },
  { name: '结算清单质控率', cells: [{ status: 'cmt', value: 9 }, { status: 'ok', value: 82 }, { status: 'ok', value: 68 }, { status: 'low', value: 11 }, { status: 'ok', value: 95 }, { status: 'ok', value: 100 }, { status: 'na' }] },
  { name: '异地就医支出占比', cells: [{ status: 'ok', value: 72 }, { status: 'ok', value: 66 }, { status: 'na' }, { status: 'na' }, { status: 'ok', value: 91 }, { status: 'ok', value: 100 }, { status: 'ok', value: 88 }] },
  { name: '单病例费用明细', internal: true, cells: [{ status: 'int' }, { status: 'int' }, { status: 'int' }, { status: 'int' }, { status: 'int' }, { status: 'int' }, { status: 'int' }] },
]

export const COCKPIT_SEED: CockpitData = {
  identities: [
    {
      id: 'conv',
      tab: '市医保局',
      name: '陈志远',
      orgShort: '示例市医保局',
      role: '召集人',
      scope: '全域 · 可下钻至诊疗行为',
      org: '示例市医疗保障局',
      zone: '分析监测区',
      title: '医保数据公开全景图',
      views: ['bub', 'inst', 'flow', 'pub'],
      ...convView('月'),
      periods: { 季: convView('季'), 年: convView('年') },
      tornTitle: '病组差额 · 逆差与结余',
      effTitle: '效 · 支付效率',
      errTitle: '错 · 审核监管',
      alertTitle: '实时提醒',
      alerts: [
        { type: '预警', text: '某肛肠专科医院 · GG19 次均 +23.6%', date: '09-08' },
        { type: '预警', text: '甲县人民医院 · ES35 再住院 8.7%', date: '09-08' },
        { type: '关注', text: '第三人民医院 · BR25 临界区 41%', date: '09-08' },
        { type: '意见', text: '第一人民医院 · BR25 核对期异议', date: '10-03' },
        { type: '关注', text: '医保外费用占比 · 3 家高于 P90', date: '09-12' },
      ],
      loopTitle: '五环节闭环',
      loop: [
        { name: '01 归集', value: '9/10', pct: 90, sub: '异地就医数据待到', status: 'warn' },
        { name: '02 配置', value: '46', pct: 93, sub: '指标 · 3 项本期暂缓', status: 'ok' },
        { name: '03 洞察', value: '7', pct: 60, sub: '候选选题 · 2 已采纳', status: 'ok' },
        { name: '04 发布', value: '3', pct: 50, sub: '待召集人审批', status: 'act' },
        { name: '05 反馈', value: '78%', pct: 78, sub: '意见答复率 · 5 条超期', status: 'warn' },
      ],
      flowNote: '来源:省平台回流,截至 2026-08-31 · 异地就医基金支出 4.87 亿,占 11.6% · 就医地机构排名仅医保局可见',
      xLabel: '病例数(例)',
      screen2Title: '公开与反馈 · 运行监测',
      screen2Right: '公开矩阵 · 指标 × 受众',
      alarmSub: '已推送召集人与行政管理组 · 3 分钟内未确认将升级',
      recipients: ['陈志远 · 召集人', '行政管理组', '委托分析团队', '专家组'],
      todo: '审批 3 · 督办 5',
    },
    {
      id: 'hosp',
      tab: '定点医药机构',
      name: '李敏',
      orgShort: '示例市第一人民医院',
      role: '医保办主任',
      scope: '本院具名 · 同级匿名分位',
      org: '示例市第一人民医院',
      zone: '发布区',
      title: '本院医保数据公开全景图',
      views: ['bub', 'dept', 'peer'],
      ...hospView('月'),
      periods: { 季: hospView('季'), 年: hospView('年') },
      ownDrgs: OWN_DRGS.map(ownDrgPeriods),
      tornTitle: '本院病组 · 超支与结余',
      effTitle: '效 · 本院诊疗效率',
      errTitle: '错 · 审核与质控',
      alertTitle: '本院待办',
      alerts: [
        { type: '待签收', text: '8月 DRG 月度运行报告', date: '09-12' },
        { type: '核对', text: 'BR25 专题核对稿 · 剩 2 天', date: '10-03' },
        { type: '提醒函', text: 'IU29 例均差额超阈值 · 待回执', date: '09-28' },
        { type: '答复', text: '医保外费用口径 · 已答复', date: '09-20' },
      ],
      loopTitle: '本院医保闭环',
      loop: [
        { name: '结算上传', value: '3,412', pct: 100, sub: '例 · 及时率 98.6%', status: 'ok' },
        { name: '分组入组', value: '99.2%', pct: 99, sub: '未入组 27 例', status: 'ok' },
        { name: '报告接收', value: '5', pct: 100, sub: '份 · 本期', status: 'ok' },
        { name: '签收核对', value: '4/5', pct: 80, sub: '1 份待签收', status: 'act' },
        { name: '意见整改', value: '2', pct: 50, sub: '条 · 1 条待复', status: 'warn' },
      ],
      flowNote: '',
      xLabel: '本院病例数(例)',
      screen2Title: '本院运行 · 对标与待办',
      screen2Right: '同级对标 · 市三级 6 家',
      alarmSub: '需在 10 个工作日内回执 · 已推送医保办主任',
      recipients: ['李敏 · 医保办', '院长办公室', '质控科', '财务科'],
      todo: '签收 1 · 核对 1',
    },
  ],
  drgs: ([
    { code: 'BR25', name: '脑缺血性疾患,伴并发症', cases: 1420, diff: 1860, cost: 14200 },
    { code: 'ES35', name: '呼吸系统感染/炎症,伴并发症', cases: 1280, diff: 240, cost: 9800 },
    { code: 'RE19', name: '恶性增生性疾患化学治疗', cases: 1160, diff: -420, cost: 6900 },
    { code: 'EX25', name: '慢性阻塞性肺病,伴并发症', cases: 960, diff: -180, cost: 11200 },
    { code: 'FR45', name: '心绞痛', cases: 880, diff: -320, cost: 7600 },
    { code: 'GG19', name: '肛门及肛周手术', cases: 820, diff: 980, cost: 7400 },
    { code: 'OB29', name: '阴道分娩', cases: 720, diff: -140, cost: 4300 },
    { code: 'IU29', name: '骨病及其他关节病', cases: 690, diff: 1420, cost: 9600 },
    { code: 'KS15', name: '糖尿病,伴并发症', cases: 640, diff: 120, cost: 8200 },
    { code: 'OF19', name: '剖宫产', cases: 610, diff: -260, cost: 8900 },
    { code: 'FT35', name: '高血压', cases: 610, diff: -90, cost: 5200 },
    { code: 'GZ15', name: '其他消化系统诊断', cases: 540, diff: 90, cost: 6100 },
    { code: 'FM19', name: '经皮心血管操作及支架置入', cases: 520, diff: -2150, cost: 38600 },
    { code: 'GU25', name: '食管炎、胃肠炎', cases: 470, diff: 60, cost: 4800 },
    { code: 'DT19', name: '中耳炎及上呼吸道感染', cases: 430, diff: -60, cost: 3400 },
    { code: 'LR15', name: '肾衰竭', cases: 380, diff: 210, cost: 12800 },
    { code: 'HS25', name: '肝硬化', cases: 270, diff: 330, cost: 13600 },
    { code: 'NS15', name: '女性生殖系统其他疾患', cases: 220, diff: 40, cost: 5600 },
    { code: 'IC29', name: '髋、膝关节置换', cases: 210, diff: 2680, cost: 62000 },
    { code: 'BR11', name: '颅内出血性疾患,伴严重并发症', cases: 190, diff: 2950, cost: 48000 },
    { code: 'IB39', name: '脊柱融合手术', cases: 160, diff: -1680, cost: 71000 },
    { code: 'JR15', name: '乳房良性病变', cases: 150, diff: -110, cost: 6800 },
    { code: 'AH29', name: '气管切开伴呼吸机支持≥96小时', cases: 45, diff: -2600, cost: 168000 },
  ] as Omit<CockpitDrg, 'byPeriod'>[]).map(drgPeriods),
  errFlag: ['BR25', 'GG19', 'IU29', 'ES35'],
  effFlag: ['BR11', 'IC29', 'AH29', 'LR15'],
  districts: ['市区', '丙区', '甲县', '乙县'],
  tiers: ['市三级', '县三级', '二级甲等', '二级其他', '一级'],
  institutions: ([
    { name: '第一人民医院', district: '市区', tier: 0, diff: 486 },
    { name: '第二人民医院', district: '丙区', tier: 0, diff: -639 },
    { name: '第三人民医院', district: '市区', tier: 0, diff: 1120 },
    { name: '市中医院', district: '丙区', tier: 0, diff: 684 },
    { name: '市妇幼保健院', district: '市区', tier: 0, diff: 513 },
    { name: '市肿瘤医院', district: '丙区', tier: 0, diff: -640 },
    { name: '甲县人民医院', district: '甲县', tier: 1, diff: 712 },
    { name: '乙县人民医院', district: '乙县', tier: 1, diff: 801 },
    { name: '甲县中医院', district: '甲县', tier: 1, diff: -138 },
    { name: '乙县中医院', district: '乙县', tier: 1, diff: 594 },
    { name: '甲县第1医院', district: '甲县', tier: 2, diff: -205 },
    { name: '乙县第2医院', district: '乙县', tier: 2, diff: 405 },
    { name: '市区第3医院', district: '市区', tier: 2, diff: 180 },
    { name: '丙区第4医院', district: '丙区', tier: 2, diff: 300 },
    { name: '甲县第5医院', district: '甲县', tier: 2, diff: -250 },
    { name: '乙县第6医院', district: '乙县', tier: 2, diff: -175 },
    { name: '市区第7医院', district: '市区', tier: 2, diff: -400 },
    { name: '丙区第8医院', district: '丙区', tier: 2, diff: -280 },
    { name: '甲县第9医院', district: '甲县', tier: 2, diff: 70 },
    { name: '某肛肠专科医院', district: '乙县', tier: 3, diff: 980 },
    { name: '市区专科医院1', district: '市区', tier: 3, diff: -70 },
    { name: '丙区专科医院2', district: '丙区', tier: 3, diff: -10 },
    { name: '甲县专科医院3', district: '甲县', tier: 3, diff: -240 },
    { name: '乙县专科医院4', district: '乙县', tier: 3, diff: -265 },
    { name: '市区专科医院5', district: '市区', tier: 3, diff: -50 },
    { name: '丙区专科医院6', district: '丙区', tier: 3, diff: 10 },
    { name: '甲县专科医院7', district: '甲县', tier: 3, diff: -220 },
    { name: '乙县专科医院8', district: '乙县', tier: 3, diff: -245 },
    { name: '市区专科医院9', district: '市区', tier: 3, diff: -30 },
    { name: '丙区专科医院10', district: '丙区', tier: 3, diff: 405 },
    { name: '市区社区1', district: '市区', tier: 4, diff: -70 },
    { name: '丙区社区2', district: '丙区', tier: 4, diff: -60 },
    { name: '甲县社区3', district: '甲县', tier: 4, diff: 54 },
    { name: '乙县社区4', district: '乙县', tier: 4, diff: 134 },
    { name: '市区社区5', district: '市区', tier: 4, diff: -62 },
    { name: '丙区社区6', district: '丙区', tier: 4, diff: -52 },
    { name: '甲县社区7', district: '甲县', tier: 4, diff: 62 },
    { name: '乙县社区8', district: '乙县', tier: 4, diff: 142 },
    { name: '市区社区9', district: '市区', tier: 4, diff: -54 },
    { name: '丙区社区10', district: '丙区', tier: 4, diff: 12 },
    { name: '甲县社区11', district: '甲县', tier: 4, diff: 32 },
    { name: '乙县社区12', district: '乙县', tier: 4, diff: 156 },
    { name: '市区社区13', district: '市区', tier: 4, diff: 92 },
    { name: '丙区社区14', district: '丙区', tier: 4, diff: 20 },
    { name: '甲县社区15', district: '甲县', tier: 4, diff: 40 },
    { name: '乙县社区16', district: '乙县', tier: 4, diff: 164 },
    { name: '市区社区17', district: '市区', tier: 4, diff: 100 },
    { name: '丙区社区18', district: '丙区', tier: 4, diff: 28 },
    { name: '甲县社区19', district: '甲县', tier: 4, diff: 48 },
    { name: '乙县社区20', district: '乙县', tier: 4, diff: -146 },
    { name: '市区社区21', district: '市区', tier: 4, diff: 150 },
    { name: '丙区社区22', district: '丙区', tier: 4, diff: 78 },
  ] as Omit<CockpitInstitution, 'byPeriod'>[]).map(instPeriods),
  flowOrigin: { name: '示例市', sub: '参保地 · 4,286 人次' },
  flows: [
    { name: '省会市', scope: '省内', share: 38.2, amount: '1.86亿' },
    { name: '邻市', scope: '省内', share: 14.5, amount: '7,062万' },
    { name: '外省A市', scope: '省外', share: 12.1, amount: '5,893万' },
    { name: '外省B市', scope: '省外', share: 9.8, amount: '4,773万' },
    { name: '外省C市', scope: '省外', share: 6.4, amount: '3,117万' },
    { name: '其他地区', scope: '—', share: 19.0, amount: '9,253万' },
  ],
  audiences: ['市三级', '县三级', '二级', '一级', '县区医保', '专家组', '省级'],
  matrixSummary: {
    unpublished: MATRIX.flatMap(r => r.cells).filter(c => c.status === 'np').length,
    unread: MATRIX.flatMap(r => r.cells).filter(c => c.status === 'low').length,
    unanswered: MATRIX.flatMap(r => r.cells).filter(c => c.status === 'cmt').length,
  },
  matrix: MATRIX,
  depts: ([
    { name: '神经内科', cases: 612, diff: 1240, cmi: 1.18 },
    { name: '骨科', cases: 356, diff: 980, cmi: 1.42 },
    { name: '老年医学科', cases: 146, diff: 1620, cmi: 1.12 },
    { name: '呼吸内科', cases: 488, diff: 210, cmi: 0.96 },
    { name: '泌尿外科', cases: 182, diff: 160, cmi: 1.05 },
    { name: '消化内科', cases: 274, diff: 90, cmi: 0.88 },
    { name: '普外科', cases: 318, diff: -120, cmi: 1.08 },
    { name: '妇产科', cases: 248, diff: -140, cmi: 0.71 },
    { name: '肿瘤科', cases: 296, diff: -380, cmi: 0.92 },
    { name: '康复科', cases: 58, diff: 420, cmi: 0.64 },
    { name: '心血管内科', cases: 402, diff: -860, cmi: 1.31 },
    { name: '重症医学科', cases: 32, diff: -2140, cmi: 3.86 },
  ] as Omit<CockpitDept, 'byPeriod'>[]).map(deptPeriods),
  peers: PEERS,
  peerSummary: PEER_SUMMARY,
  subscription: {
    frequencies: ['每日 08:00', '每周一 08:30', '每月 5 日'],
    whenLabels: ['每日 08:00', '周一 08:30', '每月 5 日'],
    contents: ['大屏快照', '核心指标摘要', '告警汇总', '待办提醒'],
    channels: ['政务微信', '邮件', '短信'],
  },
  dataAsOf: '2026-09-30',
  periodLabels: { 月: '2026年8月', 季: '2026年Q2', 年: '2026年1–8月' },
  periodMonths: { 月: 1, 季: 3, 年: 8 },
}

/**
 * ACTIONS:
 * saveSubscription({ identity: IdentityId, enabled?: boolean, frequency: string, contents: string[], channel: string, recipients: string[] })
 *   — 订阅推送 modal「保存订阅」/「停用订阅」: 落库 cockpit_subscription(登录用户 × 视角),
 *     服务端校验至少 1 项内容、至少 1 名接收人、频率 / 渠道 / 接收人在可选范围内;返回已保存的订阅,
 *     之后 GET /pages/cockpit 在 identities[].savedSubscription 中回显。
 * ackAlarm({ identity: IdentityId, type: string, text: string })
 *   — alarm banner「确认处置」: 落库 cockpit_alarm_ack(医保局共用一份;医院按本院),
 *     之后 GET /pages/cockpit 在 identities[].alerts[].acked 中回显,跨会话不再弹出。
 */
