import { http } from './http'

/** 机构门户分析（B2 / B3 / B7）。接口按当前会话机构只返回本院数据。 */

// ---------------------------------------------------------------- B2 病组下钻与专题

export interface GroupRef {
  code: string
  name: string
}

export interface GroupIndex {
  periodLabel: string
  peerGroup: string
  peerCount: number
  /** 本院重点病组（病例 ≥ 30） */
  groups: GroupRef[]
  /** 小样本病组（病例 < 30，已并入其他；不下发数值） */
  small: GroupRef[]
  other: { cases: number; avgDiff: number }
  topics: { title: string; status?: string; page: string; linkLabel: string }[]
}

export interface GroupDetail {
  code: string
  name: string
  periodLabel: string
  cases: number
  avgCost: number
  avgDiff: number
  /** 次均费用同级分位 */
  costPct: number
  costPctConcern: boolean
  mix: { category: string; own: number; bench: number }[]
  behaviors: { name: string; ownPct: number; peerMedianPct: number; deviation: number; concern: boolean }[]
  gaps: { name: string; own: number; bench: number; gap: number; unit: string; gapUnit: string; decimals: number; worse: boolean }[]
}

// ---------------------------------------------------------------- B3 对标与PK

export interface BenchIndex {
  periodLabel: string
  peerGroup: string
  peerCount: number
  tierNames: string[]
  indicators: { indicator: string; tier: number; tierName: string }[]
}

export interface BenchDetail {
  indicator: string
  /** 0 匿名分位 / 1 匿名编号 / 2 具名PK与排行 */
  tier: number
  tierName: string
  unit: string
  decimals: number
  signed: boolean
  higherIsBetter: boolean
  note: string
  periodLabel: string
  peerGroup: string
  peerCount: number
  /** 仅匿名分位档 */
  percentile?: {
    ownValue: number
    p25?: number
    p50?: number
    p75?: number
    ownPct?: number
    concern: boolean
    suppressed: boolean
    message?: string
  }
  /** 仅匿名编号档（无名称） */
  anonymous?: { label: string; value: number; own: boolean }[]
  /** 仅具名PK与排行档 */
  named?: { ownName: string; ownRank: number; rows: { rank: number; name: string; value: number; own: boolean }[] }
}

// ---------------------------------------------------------------- B7 区域外数据

export interface Offsite {
  source: string
  asOf: string
  scopeNote: string
  visits: number
  fundYi: number
  fundSharePct: number
  capacity?: { visits: number; note: string }
  diseases: { name: string; visits: number; fundSharePct: number }[]
  regions: { name: string; scope: string; fundSharePct: number }[]
  levels: { level: string; sharePct: number }[]
}

export const portalAnalysisApi = {
  groups: () => http.get<GroupIndex>('/portal/groups'),
  group: (code: string) => http.get<GroupDetail>(`/portal/groups/${encodeURIComponent(code)}`),
  benchmarks: () => http.get<BenchIndex>('/portal/benchmark'),
  benchmark: (indicator: string) => http.get<BenchDetail>(`/portal/benchmark/${encodeURIComponent(indicator)}`),
  offsite: () => http.get<Offsite>('/portal/offsite'),
}

// ---------------------------------------------------------------- 展示格式

const zh = (v: number, d = 0) => v.toLocaleString('zh-CN', { minimumFractionDigits: d, maximumFractionDigits: d })

/** 带正负号（− 为全角减号，与设计稿一致）。 */
export const signed = (v: number, d = 0) => (v > 0 ? '+' : v < 0 ? '−' : '') + zh(Math.abs(v), d)

/** 按指标属性格式化数值：小数位、正负号；unit 由调用方决定是否拼接。 */
export function fmtValue(v: number, d: { decimals: number; signed: boolean }) {
  return d.signed ? signed(v, d.decimals) : zh(v, d.decimals)
}

export const fmtNum = zh
