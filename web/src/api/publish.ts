import { http } from './http'
import type { Message, Tone } from './types'

/** A8 发布工作流：类型与接口（/api/v1/publish/**）。 */

export interface TodoItem {
  type: 'flow' | 'tier' | 'indicator'
  id: number
  name: string
  status: string
  statusTone: Tone
  due: string
  dueTone: Tone
  source?: string
}

export interface TodoGroup {
  group: string
  items: TodoItem[]
}

export interface FlowStep {
  idx: number
  name: string
  sub: string
  gate: boolean
  days: number
}

export interface FlowInfo {
  id: number
  kind: string
  name: string
  subject: string
  packageVersion: string
  step: number
  stepLabel: string
  archived: boolean
  gateIdx: number
  rejectIdx: number
  action?: '更正' | '撤回'
  reportDraftId?: number
  scopeLocked: boolean
  nextStepName?: string
}

export interface Scope {
  tiers: string[]
  districts: string[]
  batch: string
  alliance: string
  group: string
}

export type ScopeDim = keyof Scope

export interface Coverage {
  count: number
  total: number
  names: string[]
  preview: string
  byTier: { tier: string; count: number; total: number }[]
}

export interface PubLog {
  at: string
  who: string
  what: string
}

export interface Release {
  version: number
  title: string
  publishedOn: string
  signed: number
  total: number
  status: 'CURRENT' | 'SUPERSEDED' | 'WITHDRAWN'
  note: string
}

/** 批准发布后第 6 步起的推进(定向发布 → 签收查阅 → 意见申诉 → 答复整改 → 归档)。 */
export interface Advance {
  toStep: number
  toName: string
  label: string
  hint: string
  allowed: boolean
  blocked?: string
  signed?: number
  total?: number
  opinions: number
  openOpinions: number
}

export interface FlowDetail {
  flow: FlowInfo
  steps: FlowStep[]
  pkg: { items: { name: string; detail: string }[]; excluded: string[] }
  scope: Scope
  options: Record<ScopeDim, { value: string; label: string }[]>
  /** 引擎不可用时为空，覆盖数显示「—」。 */
  coverage?: Coverage
  logs: PubLog[]
  corrections: { subject: string; releases: Release[]; action?: string; explanation?: string; canInitiate: boolean }
  openCheckOpinions: number
  canApprove: boolean
  advance?: Advance
}

export interface AudienceVersion {
  id: number
  title: string
  subtitle: string
  note?: string
  noteTone?: Tone
  lines: { label: string; value: string; tone: 'default' | 'danger' | 'success' | 'muted' }[]
}

export interface TierRequest {
  id: number
  indicator: string
  fromTier: number
  toTier: number
  fromName: string
  toName: string
  currentTier: number
  reason: string
  approver: string
  requestedBy: string
  requestedAt: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  named: boolean
}

export interface IndicatorRequest {
  id: number
  approvalNo: string
  code: string
  name: string
  grp: string
  domain: string
  formula: string
  scope: string
  internal: boolean
  tier?: number
  tierName?: string
  owner: string
  requestedBy: string
  requestedAt: string
  status: string
  template: string
  unit: string
}

type Step = Message & { step: number }

export const publishApi = {
  todos: () => http.get<TodoGroup[]>('/publish/todos'),
  flow: (id: number) => http.get<FlowDetail>(`/publish/flows/${id}`),
  scope: (id: number, scope: Scope) => http.put<{ scope: Scope; coverage: Coverage }>(`/publish/flows/${id}/scope`, scope),
  submit: (id: number) => http.post<Message>(`/publish/flows/${id}/submit`),
  advance: (id: number) => http.post<Step>(`/publish/flows/${id}/advance`),
  approve: (id: number, opinion: string) => http.post<Step>(`/publish/flows/${id}/approve`, { opinion }),
  reject: (id: number, opinion: string) => http.post<Step>(`/publish/flows/${id}/reject`, { opinion }),
  correct: (id: number, action: '更正' | '撤回', reason: string) =>
    http.post<Message & { id: number }>(`/publish/flows/${id}/corrections`, { action, reason }),
  audienceVersions: () => http.get<AudienceVersion[]>('/publish/audience-versions'),
  tierRequest: (id: number) => http.get<TierRequest>(`/publish/tier-requests/${id}`),
  approveTier: (id: number, opinion: string) => http.post<Message>(`/publish/tier-requests/${id}/approve`, { opinion }),
  rejectTier: (id: number, opinion: string) => http.post<Message>(`/publish/tier-requests/${id}/reject`, { opinion }),
  indicatorRequest: (id: number) => http.get<IndicatorRequest>(`/publish/indicator-requests/${id}`),
  approveIndicator: (id: number, opinion: string) => http.post<Message>(`/publish/indicator-requests/${id}/approve`, { opinion }),
  rejectIndicator: (id: number, opinion: string) => http.post<Message>(`/publish/indicator-requests/${id}/reject`, { opinion }),
}
