export interface ApiError {
  code: string
  message: string
}

export type Tone = 'success' | 'warning' | 'danger' | 'primary' | 'ai' | 'muted'

export interface Message {
  message: string
}

// ---------------------------------------------------------------- 认证（A1）
export interface Cert {
  holder: string
  org: string
  issuer: string
  expires: string
}

export interface AuthConfig {
  cert: Cert | null
  demo: boolean
  demoSmsCode?: string
  demoAccounts?: string[]
}

export interface Identity {
  id: number
  role: string
  roleLabel: string
  org: string
  orgDetail: string
  scope: string
  home: string
  homeLabel: string
}

export interface LoginResult {
  ticket: string
  name: string
  identities: Identity[]
}

export interface Me {
  username: string
  name: string
  role: string
  roleLabel: string
  org: string
  scope: string
  pages: string[]
}

// ---------------------------------------------------------------- 数据归集（A3）
export type SourceStatus = 'OK' | 'LATE' | 'PART'

export interface DataSource {
  id: number
  name: string
  provider: string
  mode: string
  frequency: string
  status: SourceStatus
  statusLabel: string
  score?: number
  dueDate?: string
}

export interface PipelineStep {
  no: string
  name: string
  state: 'done' | 'run' | 'wait'
  count: string
}

export interface OrgQc {
  org: string
  comorbidityPct: number
  listQcPct: number
  comorbidityLow?: boolean
  listQcLow?: boolean
}

export interface CollectionOverview {
  period: string
  title: string
  allArrived: boolean
  qcDone: boolean
  pipeline: PipelineStep[]
  sources: DataSource[]
  quality: { completenessPct: number; consistencyPct: number; timelinessPct?: number; orgs: OrgQc[]; engineAvailable: boolean }
  lineageIndicators: string[]
}

export interface SourceDetail {
  id: number
  name: string
  late: boolean
  dueDate?: string
  indicators: { name: string; suspended: boolean }[]
}

export interface LineageStep {
  key: string
  value: string
  missing: boolean
}

// ---------------------------------------------------------------- 模板（A5）
export interface ChartTemplate {
  id: number
  name: string
  useCase: string
  example: string
  tierScope: string
  usedBy: number
  version: string
  updated: string
}

export interface ReportBlock {
  id: number
  name: string
  height: number
}

export interface ReportPreset {
  name: string
  blockIds: number[]
}

// ---------------------------------------------------------------- 病组专题（A7）
export interface TopicSection {
  idx: number
  name: string
  draft: string
  draftVersion: number
  approved: boolean
  approvedBy?: string
  approvedAt?: string
}

export interface Behavior {
  name: string
  ratePct: number
  withAvg: number
  withoutAvg: number
  multiplier: number
  level: 'high' | 'elevated'
}

export interface WaterfallBar {
  label: string
  base: number
  height: number
  value: number
  kind: 'total' | 'delta'
}

export interface Topic {
  code: string
  name: string
  period: string
  source: string
  overview: {
    cases: number
    orgs: number
    avgCost: number
    yoyPct: number
    avgDiff: number
    diffTotalWan: number
    los: number
    peerLos: number
    benchOrgs: number
    deviantOrgs: number
    levels: { name: string; pct: number }[]
  }
  costMix: { categories: string[]; rows: { name: string; values: number[] }[] }
  behaviors: { rows: Behavior[]; topMultiplier: string; topRate: string }
  attribution: { cityMean: number; patientDiff: number; behaviorDiff: number; r2: number }
  waterfall: { bars: WaterfallBar[]; totalGap: number; patientSharePct: number; behaviorSharePct: number; axisMax: number }
  benchmark: { metric: string; bench: string; deviant: string; gap: string }[]
  optimization: { totalWan: number; formula: string; items: { name: string; wan: number }[] }
  suggestions: { insurer: string[]; hospital: string[] }
  sections: TopicSection[]
  approvedCount: number
  submitted: boolean
}

// ---------------------------------------------------------------- 流程设计器（A9）
export interface FlowTemplateRow {
  id: number
  kind: string
  version: string
  pendingVersion?: string
  updatedBy: string
  updatedOn: string
}

export interface FlowNode {
  idx: number
  name: string
  handler: string
  mode: string
  days: number
  escalateTo: string
  gate: boolean
}

// ---------------------------------------------------------------- 意见（A10）
export interface Ticket {
  no: string
  org: string
  ref: string
  category: string
  owner: string
  dueLabel: string
  dueTone: Tone
  status: 'WAIT' | 'DOING' | 'DONE'
  content: string
  reply?: string
  rating?: number
  typical: boolean
}

// ---------------------------------------------------------------- 预警（A11）
export interface AlertRule {
  id: number
  name: string
  scope: string
  condition: string
  frequency: string
  hits: number
  enabled: boolean
}

export interface AlertTrigger {
  id: number
  date: string
  org: string
  group: string
  rule: string
  value: string
  status: 'GEN' | 'SENT' | 'RCPT' | 'FIX' | 'CLOSED'
  receipt?: string
  letter: { no: string; org: string; group: string; rule: string; value: string; period: string }
  track: { name: string; desc: string; done: boolean }[]
}

// ---------------------------------------------------------------- 权限（A12）
export interface OrgUnit {
  id: number
  name: string
  level: number
  virtual: boolean
}

export interface PermRole {
  id: number
  name: string
  matrix: string[]
  dims: { key: string; value: string }[]
}

export interface AccountRow {
  id: number
  name: string
  org: string
  role: string
  stage: string
  tone: Tone
  note: string
}

// ---------------------------------------------------------------- 展示策略（A13）
export interface Quadrant {
  id: number
  title: string
  summary: string
  tone: Tone
  audience: string
  granularity: string
  naming: string
  threshold: string
  fixedNone: boolean
}

export interface TierRow {
  indicator: string
  tier: number
  pending?: { id: number; toTier: number; approver: string }
}

// ---------------------------------------------------------------- 审计（A14）
export interface AuditLog {
  id: number
  at: string
  user: string
  org: string
  type: string
  object: string
  ip?: string
  watermarkNo?: string
  result: string
}

export interface AuditTrace {
  user: string
  org: string
  at: string
  type: string
  object: string
  ip?: string
}
