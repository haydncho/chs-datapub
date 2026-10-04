import { http } from './http'

/** 全息图（A2 医保局 / B1 机构门户）接口与类型。派生数字（半径、色阶、关键少数、外环、占比、分位着色）均由引擎计算。 */

export type HoloPeriod = '月' | '季' | '年'
export type Band = 'deficit-high' | 'deficit-mid' | 'deficit-low' | 'balanced' | 'surplus-low' | 'surplus-high'

export interface Tick {
  value: number
  label: string
}

export interface HoloAxis {
  xMax: number
  xTicks: Tick[]
  yMax: number
  yTicks: Tick[]
}

export interface HoloBubble {
  code: string
  name: string
  shortName: string
  cases: number
  avgDiff: number
  avgCost: number
  totalDiff: number
  totalWan: number
  radius: number
  band: Band
  isKey: boolean
  effFlag: boolean
  errFlag: boolean
}

export interface Panorama {
  period: HoloPeriod
  multiplier: number
  groups: HoloBubble[]
  keyCount: number
  keyDeficit: number
  keyDeficitWan: number
  axis: HoloAxis
  bands: { key: Band; label: string }[]
}

export type CellStatus = 'ok' | 'low' | 'np' | 'cmt' | 'int' | 'na'

export interface Publication {
  audiences: string[]
  rows: { name: string; grp: string; internal: boolean; cells: { status: CellStatus; value?: number; text: string }[] }[]
  counts: { np: number; low: number; cmt: number }
  status: { target: string; tier: string; latest: string; signPct: number; readPct: number; opinions: number; replyPct: number; overdue: number }
}

export interface HoloOverview {
  period: HoloPeriod
  periodLabel: string
  panorama: Panorama
  publication: Publication
  shortcuts: { kind: string; title: string; sub: string }[]
}

export interface GroupDetail {
  code: string
  name: string
  scope: string
  cases: number
  avgDiff: number
  avgCost: number
  totalDiff: number
  totalWan: number
  direction: '逆差' | '结余'
  isKey: boolean
  peerPct: number
  peerLevel: 'danger' | 'warning' | 'primary'
  costMix: { name: string; mine: number; bench: number }[]
  behaviors: { name: string; ratePct: number; multiplier: number }[]
  gaps: { metric: string; value: string; bench: string; gap: string; worse: boolean }[]
}

export interface Offsite {
  source: string
  totalText: string
  fundSharePct: number
  visits: number
  flows: { region: string; scope: '省内' | '省外' | '其他'; amountWan: number; amountText: string; sharePct: number; strokeWidth: number }[]
  levels: { name: string; pct: number }[]
  diseases: { name: string; pct: number }[]
  /** 就医地机构排名：仅医保局身份下发。 */
  ranking?: { facility: string; visits: number }[]
}

export interface HospitalHolo {
  org: { name: string; tier: string; peerCount: number; period: string; tierMode: string; pendingSign?: string; alert?: string }
  own: { code: string; name: string; shortName: string; cases: number; avgDiff: number; radius: number; direction: '逆差' | '结余'; faded: boolean; peerDiff?: number }[]
  /** 同级同组均值：只有病组编码与均值，无任何机构字段。 */
  peers: { code: string; cases: number; avgDiff: number; radius: number }[]
  /** 本院病例 < 30 的病组合并为“其他”。 */
  merged: { count: number; cases: number; avgDiff: number } | null
  axis: HoloAxis
  indicators: { suppressed: boolean; message: string; rows: { name: string; value: string; unit: string; pct: number; tone: 'warning' | 'primary'; note: string }[] }
  ledger: { ledgerWan: number; drgPayWan: number; deviationWan: number; direction: '逆差' | '结余'; deviationPct: number; avgDiff: number; diffPct: number }
  trend: { month: string; avgDiff: number; heightPct: number }[]
  top: { code: string; shortName: string; cases: number; avgDiff: number }[]
}

export const holoApi = {
  overview: (period: HoloPeriod) => http.get<HoloOverview>('/holo/overview', { period }),
  group: (code: string, period: HoloPeriod) => http.get<GroupDetail>(`/holo/groups/${encodeURIComponent(code)}`, { period }),
  offsite: () => http.get<Offsite>('/holo/offsite'),
  hospital: () => http.get<HospitalHolo>('/portal/holo'),
}

// ---------------------------------------------------------------- 展示辅助

const nf = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 })
export const fmt = (v: number) => nf.format(Math.round(v))
/** 带符号千分位：+1,860 / −2,150。 */
export const signed = (v: number) => (Math.round(v) > 0 ? '+' : Math.round(v) < 0 ? '−' : '') + fmt(Math.abs(v))
export const wanText = (v: number) => `${v.toLocaleString('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}万`

/** 差额总额色阶 → 语义令牌（深色主题自动切换）。 */
export const BAND_COLOR: Record<Band, string> = {
  'deficit-high': 'var(--c-red-solid)',
  'deficit-mid': 'var(--c-bar-red-a)',
  'deficit-low': 'var(--c-chart-amber)',
  balanced: 'var(--c-text-faint)',
  'surplus-low': 'var(--c-chart-green)',
  'surplus-high': 'var(--c-success-solid)',
}
