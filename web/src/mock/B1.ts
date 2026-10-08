/** B1 本院全景 — seed data (same JSON the backend serves at GET /api/v1/pages/B1). */

export interface B1Kpi {
  name: string
  /** display value, already formatted ("12,860") */
  value: string
  unit: string
  /**
   * 同级分位 0–100, ranked by performance: higher = better than more of the peer group
   * (P100 = 同级最优, whatever the indicator's good direction). Below P35 is a 关注 signal.
   */
  percentile: number
  /** month-on-month change (signed) */
  mom: number
  /** 1 = higher is better, -1 = lower is better */
  goodDirection: 1 | -1
  /** last 8 months, mini-trend bar heights (px, 8–25) */
  spark: number[]
}

export interface B1Drg {
  code: string
  name: string
  /** 全市同级同组: total cases across the city peer group */
  cityCases: number
  /** 全市同组 例均基金差额 (元) */
  cityDiff: number
  /** 本院 例均费用 (元) — drives bubble size; equals B2 avgCost */
  cost: number
  /** 本院病例数 */
  cases: number
  /** 本院 例均基金差额 (元, + = 逆差) */
  diff: number
  /** 例均基金差额 同级分位 (按表现排位, 越高越好; equals B2 peerPercentile) */
  percentile: number
  /** last 6 months, signed relative movement (−3…+5) */
  trend: number[]
}

export interface B1Data {
  hospital: { name: string; subtitle: string }
  todos: { reportsToSign: number; verifyItems: number; verifyDaysLeft: number; watchItems: number }
  /** 全院 DRG 入组病例 (the listed groups sum to no more than this) */
  totalCases: number
  kpis: B1Kpi[]
  /** 医保记账 vs DRG 支付 (万元) */
  settlement: { billed: number; drgPaid: number }
  /** 口径 / 批次 shown on the 本院病组全景 tag */
  lineage: { indicator: string; version: string; batch: string; source: string }
  /** default selected DRG */
  defaultDrg: string
  drgs: B1Drg[]
  /** set by the server when the viewer's institution has no data here */
  noOwnData?: boolean
  noOwnDataNote?: string
}

export const B1_SEED: B1Data = {
  hospital: { name: '示例市第一人民医院', subtitle: '同级组 市三级 · 6 家 · 2026年8月 · 对标档位 匿名分位' },
  todos: { reportsToSign: 1, verifyItems: 1, verifyDaysLeft: 2, watchItems: 1 },
  kpis: [
    { name: 'CMI', value: '1.12', unit: '', percentile: 68, mom: 0.03, goodDirection: 1, spark: [18, 19, 20, 21, 22, 23, 24, 25] },
    { name: '次均费用', value: '12,860', unit: '元', percentile: 55, mom: -1.8, goodDirection: -1, spark: [15, 16, 17, 18, 19, 20, 21, 22] },
    { name: '费用消耗指数', value: '1.04', unit: '', percentile: 42, mom: 0.02, goodDirection: -1, spark: [16, 17, 18, 19, 20, 21, 22, 23] },
    { name: '时间消耗指数', value: '0.97', unit: '', percentile: 58, mom: -0.01, goodDirection: -1, spark: [11, 12, 13, 14, 15, 16, 17, 18] },
    { name: '医保外费用占比', value: '6.8', unit: '%', percentile: 28, mom: 1.2, goodDirection: -1, spark: [9, 10, 11, 12, 13, 14, 15, 16] },
    { name: '结算清单质控率', value: '96.4', unit: '%', percentile: 55, mom: 0.6, goodDirection: 1, spark: [23, 24, 25, 8, 9, 10, 11, 12] },
  ],
  totalCases: 3412,
  settlement: { billed: 4862.4, drgPaid: 4615.7 },
  lineage: { indicator: '例均基金差额', version: 'v2.1', batch: 'B20260905-03', source: '结算明细 2026-08' },
  defaultDrg: 'BR25',
  drgs: [
    { code: 'BR25', name: '脑缺血性疾患,伴并发症', cityCases: 1420, cityDiff: 1860, cost: 14820, cases: 286, diff: 2140, percentile: 48, trend: [3, -1, 4, 0, 5, 1] },
    { code: 'ES35', name: '呼吸系统感染/炎症,伴并发症', cityCases: 1280, cityDiff: 240, cost: 9640, cases: 312, diff: 180, percentile: 51, trend: [0, 1, 2, 3, 4, 5] },
    { code: 'RE19', name: '恶性增生性疾患化学治疗', cityCases: 1160, cityDiff: -420, cost: 7630, cases: 250, diff: 310, percentile: 35, trend: [0, 1, 2, 3, 4, 5] },
    { code: 'EX25', name: '慢性阻塞性肺病,伴并发症', cityCases: 960, cityDiff: -180, cost: 11800, cases: 218, diff: 420, percentile: 38, trend: [3, -1, 4, 0, 5, 1] },
    { code: 'FR45', name: '心绞痛', cityCases: 880, cityDiff: -320, cost: 8100, cases: 318, diff: 180, percentile: 40, trend: [3, -1, 4, 0, 5, 1] },
    { code: 'GG19', name: '肛门及肛周手术', cityCases: 820, cityDiff: 980, cost: 7900, cases: 216, diff: 1480, percentile: 44, trend: [-3, -3, -3, -3, -3, -3] },
    { code: 'OB29', name: '阴道分娩', cityCases: 720, cityDiff: -140, cost: 4240, cases: 260, diff: -195, percentile: 51, trend: [3, 2, 1, 0, -1, -2] },
    { code: 'IU29', name: '骨病及其他关节病', cityCases: 690, cityDiff: 1420, cost: 11020, cases: 168, diff: 1640, percentile: 48, trend: [0, -2, 5, 3, 1, -1] },
    { code: 'KS15', name: '糖尿病,伴并发症', cityCases: 640, cityDiff: 120, cost: 8540, cases: 210, diff: 460, percentile: 43, trend: [3, 2, 1, 0, -1, -2] },
    { code: 'OF19', name: '剖宫产', cityCases: 610, cityDiff: -260, cost: 9370, cases: 152, diff: 210, percentile: 41, trend: [0, 4, -1, 3, -2, 2] },
    { code: 'FT35', name: '高血压', cityCases: 610, cityDiff: -90, cost: 5590, cases: 171, diff: 300, percentile: 42, trend: [-3, 3, 0, -3, 3, 0] },
    { code: 'GZ15', name: '其他消化系统诊断', cityCases: 540, cityDiff: 90, cost: 6530, cases: 125, diff: 520, percentile: 41, trend: [0, 1, 2, 3, 4, 5] },
    { code: 'FM19', name: '经皮心血管操作及支架置入', cityCases: 520, cityDiff: -2150, cost: 40130, cases: 157, diff: -620, percentile: 41, trend: [3, 2, 1, 0, -1, -2] },
    { code: 'GU25', name: '食管炎、胃肠炎', cityCases: 470, cityDiff: 60, cost: 4980, cases: 161, diff: 240, percentile: 46, trend: [0, -2, 5, 3, 1, -1] },
    { code: 'LR15', name: '肾衰竭', cityCases: 380, cityDiff: 210, cost: 13570, cases: 115, diff: 980, percentile: 35, trend: [3, 2, 1, 0, -1, -2] },
    { code: 'HS25', name: '肝硬化', cityCases: 270, cityDiff: 330, cost: 14480, cases: 85, diff: 1210, percentile: 32, trend: [-3, -3, -3, -3, -3, -3] },
    { code: 'IC29', name: '髋、膝关节置换', cityCases: 210, cityDiff: 2680, cost: 63420, cases: 69, diff: 4100, percentile: 44, trend: [-3, -3, -3, -3, -3, -3] },
    { code: 'BR11', name: '颅内出血性疾患,伴严重并发症', cityCases: 190, cityDiff: 2950, cost: 48450, cases: 66, diff: 3400, percentile: 48, trend: [-3, 3, 0, -3, 3, 0] },
  ],
}

/** the institution whose own figures the seeds (B1/B2/B3/B7) are — 示例市第一人民医院 */
export const SEED_ORG = 'H001'

/** what a hospital outside the stored data sees before (or without) the server's answer */
export const B1_EMPTY: B1Data = {
  ...B1_SEED,
  hospital: { name: '', subtitle: '暂无本院数据' },
  todos: { reportsToSign: 0, verifyItems: 0, verifyDaysLeft: 0, watchItems: 0 },
  kpis: [],
  settlement: { billed: 0, drgPaid: 0 },
  defaultDrg: '',
  drgs: [],
  noOwnDataNote: '暂无本院数据',
}

/**
 * ACTIONS: none — B1 is read-only (selection, sorting and navigation to
 * B2 / B4 / B5 are pure UI; 口径 tag only shows lineage).
 */
