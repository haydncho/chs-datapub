import { http } from './http'

/** regional 分组（C1 县区 / C2 省级 / C3 外部监督）类型与接口。 */

// ---------------------------------------------------------------- C1 县区医保视图
export interface CountyInstitution {
  name: string
  level: string
  cases: number
  avgDiff: number
  avgDiffText: string
  listQcPct: number
  costPctl: number
  /** 清单质控率低于阈值（标橙） */
  qcLow: boolean
}

export interface CountySummary {
  name: string
  rank: number
  avgDiff: number
  avgDiffText: string
  listQcPct: number
  score: number
  self: boolean
}

export type MonitorStatus = 'ok' | 'warn' | 'bad'

export interface MonitorIndicator {
  name: string
  display: string
  status: MonitorStatus
  rule: string
}

export interface CountyOverview {
  county: string
  period: string
  alliancePeriod: string | null
  qcThreshold: number
  rankBasis: string
  institutions: CountyInstitution[]
  counties: CountySummary[]
  indicators: MonitorIndicator[]
  statusCounts: Record<MonitorStatus, number>
}

// ---------------------------------------------------------------- C2 省级与区域外汇总
export type ProvinceSortKey = 'name' | 'period' | 'date' | 'signPct' | 'readPct' | 'replyPct' | 'balancePct' | 'coveragePct' | 'avgDiff'
export type SortDir = 'asc' | 'desc'

export interface RegionRow {
  name: string
  self: boolean
  lastPeriod: string
  dateLabel: string
  overdue: boolean
  overdueDays: number
  signPct: number
  readPct: number
  readLow: boolean
  replyPct: number
  balancePct: number
  deficit: boolean
  coveragePct: number
  avgDiff: number
  avgDiffText: string
}

export interface ProvinceKpis {
  onTime: number
  total: number
  overdue: { name: string; days: number }[]
  avgSignPct: number
  signDeltaPt: number
  avgReplyPct: number
  lowReplyCount: number
  balancePct: number
  deficitCount: number
}

export interface ProvinceSummary {
  asOf: string
  sort: ProvinceSortKey
  dir: SortDir
  kpis: ProvinceKpis
  rows: RegionRow[]
  insights: { title: string; body: string }[]
}

// ---------------------------------------------------------------- C3 外部监督只读席位
export interface SeatMaterial {
  id: number
  name: string
  format: string
}

export interface Recording {
  id: number
  title: string
  durationS: number
  watchedS: number
}

export interface Seat {
  event: string
  validFrom: string
  validUntil: string
  validUntilIso: string
  /** 服务端时钟下的剩余秒数 */
  remainingSeconds: number
  materials: SeatMaterial[]
  recording: Recording
}

export interface MaterialPreview extends SeatMaterial {
  heading: string
  kpis: { label: string; value: string; tone: 'default' | 'success' | 'warning' | 'danger' }[]
  body: string[]
}

/** 席位到期后服务端返回的错误码。 */
export const SEAT_EXPIRED = 'SEAT_EXPIRED'

export const regionalApi = {
  county: () => http.get<CountyOverview>('/county/overview'),
  province: (sort: ProvinceSortKey, dir: SortDir) => http.get<ProvinceSummary>('/province/summary', { sort, dir }),
  seat: () => http.get<Seat>('/supervision/seat'),
  material: (id: number) => http.get<MaterialPreview>(`/supervision/materials/${id}`),
  progress: (watchedS: number) => http.post<Recording>('/supervision/recording/progress', { watchedS }),
}
