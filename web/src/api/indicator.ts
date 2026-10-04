import { http } from './http'

/* ================================================================ A4 指标可视化配置 */

export type IndTag = '必选' | '增选' | '仅内部'
export type IndStatus = '已上线' | '本期暂缓' | '审批中' | '草稿'

export interface IndRow {
  id: number
  code: string
  name: string
  tag: IndTag
  tagLabel: string
  grp: '钱' | '效' | '错'
  domain: string
  source: string
  freq: string
  tier?: number
  tierLabel: string
  /** 有待审批的档位切换单（A13）时的目标档位。 */
  tierPending?: string
  scope: string
  version: string
  status: IndStatus
  /** 本期暂缓 / 审批中的悬停说明。 */
  statusTip?: string
  internal: boolean
  inPackage: boolean
}

export interface IndPage {
  rows: IndRow[]
  total: number
  page: number
  size: number
  pages: number
  domains: string[]
  tags: IndTag[]
  packageName: string
}

export interface IndQuery {
  grp?: string
  domain?: string
  tag?: string
  q?: string
  sort?: 'tier' | 'status'
  dir?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface IndCard {
  row: IndRow
  owner: string
  formula: string
  ruleVersion: string
  lineage: string[]
  changes: { label: string; date: string; author: string; note: string }[]
}

export interface IndMeta {
  atoms: { name: string; unit: string; source: string }[]
  dims: string[]
  filterFields: string[]
  filterOps: string[]
  calcs: string[]
  functions: string[]
  peerGroups: { name: string; n: number }[]
  templates: string[]
  audiences: string[]
  granularity: string[]
  warnPcts: string[]
  previewOrgs: string[]
  tierNames: string[]
  groups: string[]
  domains: string[]
  caliber: string
}

export interface WizFilter {
  field: string
  op: string
  value: string
}

/** 向导配置（服务端草稿自动保存）。 */
export interface WizConfig {
  name: string
  grp: string
  domain: string
  numerator: string | null
  denominator: string | null
  dims: string[]
  filters: WizFilter[]
  calc: string
  formula: string
  minOrgs: number
  minCases: number
  warnPct: string
  warnRise: number
  internal: boolean
  tier: number
  template: string
  title: string
  unit: string
  note: string
  audiences: string[]
  granularity: string
}

export interface WizDraft {
  id: number
  version: string
  savedAt: string
  config: WizConfig
  submitted: boolean
  approvalNo?: string
  indicatorId?: number
}

export interface FormulaCheck {
  ok: boolean
  name?: string
  errors: { line: number; message: string }[]
  atoms: string[]
  functions: string[]
  groupBy: string[]
  estimatedCases?: number
  message: string
}

export interface GroupStat {
  name: string
  n: number
  cases: number
  suppressed: boolean
  p25?: number
  p50?: number
  p75?: number
}

/** 以机构身份预览（引擎裁剪后）：机构只拿到本院值与同级分位。 */
export interface PreviewResult {
  mode: 'normal' | 'suppressed' | 'internal' | 'all'
  org?: string
  group?: string
  peerCount?: number
  value?: number
  pct?: number
  p25?: number
  p50?: number
  p75?: number
  cityMean?: number
  note?: string
  groups?: GroupStat[]
  totalCases?: number
  tierLabel: string
  unit: string
  indicator: string
}

export interface SubmitResult {
  approvalNo: string
  indicatorId: number
  code: string
  message: string
  detail: string
  tierNote?: string
  draft: WizDraft
}

export const indicatorApi = {
  list: (q: IndQuery) => http.get<IndPage>('/indicators', { ...q }),
  card: (id: number) => http.get<IndCard>(`/indicators/${id}`),
  addToPackage: (id: number) => http.post<{ row: IndRow; message: string }>(`/indicators/${id}/package`),
  removeFromPackage: (id: number) => http.del<{ row: IndRow; message: string }>(`/indicators/${id}/package`),
  meta: () => http.get<IndMeta>('/indicators/meta'),
  validate: (formula: string, dims: string[]) => http.post<FormulaCheck>('/indicators/formula/validate', { formula, dims }),
  openDraft: () => http.post<WizDraft>('/indicators/drafts'),
  saveDraft: (id: number, config: WizConfig) => http.put<WizDraft>(`/indicators/drafts/${id}`, config),
  resetDraft: (id: number) => http.post<WizDraft>(`/indicators/drafts/${id}/reset`),
  preview: (id: number, org: string | null) => http.post<PreviewResult>(`/indicators/drafts/${id}/preview`, { org }),
  submit: (id: number) => http.post<SubmitResult>(`/indicators/drafts/${id}/submit`),
}

/* ================================================================ A6 智能推荐中心 */

export type TopicStatus = 'CAND' | 'ADOPT' | 'REJECT'

export interface TopicRow {
  code: string
  name: string
  score: number
  reasons: string[]
  engineReasons: string[]
  status: TopicStatus
  modified: boolean
  note?: string
  decidedBy?: string
  decidedAt?: string
}

export interface TopicList {
  period: string
  rows: TopicRow[]
  weights: Record<string, number>
  reasonOptions: string[]
}

export interface AttrRow {
  code: string
  name: string
  status: 'ok' | 'withheld'
  totalDiff?: number
  patientPct?: number
  behaviorPct?: number
  topBehaviors: { name: string; amount: number }[]
  reason?: string
}

export interface BenchRow {
  name: string
  n: number
  small: boolean
  bench?: number
  middle?: number
  deviant?: number
  benchLine?: number
  deviantLine?: number
}

export interface BenchResult {
  threshold: number
  rows: BenchRow[]
  rule: string
}

export interface AnomalyRow {
  id: number
  rule: string
  /** 仅医保局可见（本接口只对召集人 / 行政管理组开放）。 */
  org: string
  drg: string
  value: string
  level: '关注' | '预警'
  letterNo?: string
  letterBy?: string
  letterAt?: string
}

export interface PresentationRow {
  indicator: string
  chart: string
  reason: string
}

export interface MethodCard {
  tab: string
  title: string
  rows: [string, string][]
}

type Msg<T> = { row: T; message: string }

export const recommendApi = {
  topics: () => http.get<TopicList>('/recommend/topics'),
  decide: (code: string, action: 'adopt' | 'reject') => http.post<Msg<TopicRow>>(`/recommend/topics/${code}/decision`, { action }),
  undo: (code: string) => http.post<Msg<TopicRow>>(`/recommend/topics/${code}/undo`),
  modify: (code: string, reasons: string[], note: string) => http.put<Msg<TopicRow>>(`/recommend/topics/${code}`, { reasons, note }),
  attribution: () => http.get<{ rows: AttrRow[] }>('/recommend/attribution'),
  benchmark: (threshold: number) => http.get<BenchResult>('/recommend/benchmark', { threshold }),
  anomalies: () => http.get<AnomalyRow[]>('/recommend/anomalies'),
  letter: (id: number) => http.post<Msg<AnomalyRow>>(`/recommend/anomalies/${id}/letter`),
  presentations: () => http.get<PresentationRow[]>('/recommend/presentations'),
  methods: () => http.get<MethodCard[]>('/recommend/methods'),
}
