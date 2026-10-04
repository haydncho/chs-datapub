import { http } from './http'
import type * as T from './types'

export const authApi = {
  config: () => http.get<T.AuthConfig>('/auth/config'),
  sms: (username: string) => http.post<{ sentTo: string; resendSeconds: number }>('/auth/sms-code', { username }),
  login: (body: { method: 'CA' | 'PASSWORD'; username?: string; password?: string; smsCode?: string; pin?: string }) =>
    http.post<T.LoginResult>('/auth/login', body),
  identity: (ticket: string, identityId: number) => http.post<{ token: string; user: T.Me }>('/auth/identity', { ticket, identityId }),
  me: () => http.get<T.Me>('/auth/me'),
  logout: () => http.post<void>('/auth/logout'),
}

export const collectionApi = {
  overview: (period = '2026-08') => http.get<T.CollectionOverview>('/collection', { period }),
  source: (id: number) => http.get<T.SourceDetail>(`/collection/sources/${id}`),
  lineage: (indicator: string) => http.get<T.LineageStep[]>('/collection/lineage', { indicator }),
  arrival: (id: number) => http.post<T.Message>(`/collection/sources/${id}/arrival`),
  qualityCheck: (period = '2026-08') => http.post<T.Message>(`/collection/quality-check?period=${period}`),
}

export const templateApi = {
  charts: () => http.get<T.ChartTemplate[]>('/chart-templates'),
  blocks: () => http.get<T.ReportBlock[]>('/report-blocks'),
  presets: () => http.get<T.ReportPreset[]>('/report-presets'),
  draft: (preset: string, blockIds: number[]) =>
    http.post<{ id: number; flowStep: number; message: string }>('/report-drafts', { preset, blockIds, period: '2026-08' }),
}

export const topicApi = {
  get: (code: string) => http.get<T.Topic>(`/topics/${code}`),
  approve: (code: string, idx: number) => http.post<T.TopicSection>(`/topics/${code}/sections/${idx}/approve`),
  regenerate: (code: string, idx: number) => http.post<T.TopicSection>(`/topics/${code}/sections/${idx}/regenerate`),
  submit: (code: string) => http.post<T.Message>(`/topics/${code}/submit`),
}

export const flowApi = {
  list: () => http.get<T.FlowTemplateRow[]>('/flow-templates'),
  get: (id: number) => http.get<{ template: T.FlowTemplateRow; nodes: T.FlowNode[] }>(`/flow-templates/${id}`),
  updateNode: (id: number, idx: number, body: { mode?: string; days?: number }) => http.put<T.FlowNode>(`/flow-templates/${id}/nodes/${idx}`, body),
  deleteNode: (id: number, idx: number) => http.del<T.Message>(`/flow-templates/${id}/nodes/${idx}`),
  saveVersion: (id: number) => http.post<T.Message & { version: string }>(`/flow-templates/${id}/versions`),
}

export const opinionApi = {
  list: (tab: string) => http.get<{ counts: Record<string, number>; rows: T.Ticket[] }>('/opinions', { tab }),
  reply: (no: string, text: string) => http.post<T.Ticket>(`/opinions/${no}/reply`, { text }),
  transfer: (no: string) => http.post<T.Ticket>(`/opinions/${no}/transfer`),
  typical: (no: string, typical: boolean) => http.put<T.Ticket>(`/opinions/${no}/typical`, { typical }),
}

export const alertApi = {
  rules: () => http.get<T.AlertRule[]>('/alerts/rules'),
  toggle: (id: number, enabled: boolean) => http.put<T.AlertRule>(`/alerts/rules/${id}`, { enabled }),
  triggers: () => http.get<T.AlertTrigger[]>('/alerts/triggers'),
  send: (id: number) => http.post<T.AlertTrigger>(`/alerts/triggers/${id}/send`),
  receipt: (id: number) => http.post<T.AlertTrigger>(`/alerts/triggers/${id}/receipt`),
}

export const adminApi = {
  orgs: () => http.get<T.OrgUnit[]>('/admin/orgs'),
  roles: () => http.get<{ columns: string[]; roles: T.PermRole[] }>('/admin/roles'),
  accounts: () => http.get<T.AccountRow[]>('/admin/accounts'),
  operate: (target: string, action: string) => http.post<T.Message>('/admin/operations', { target, action }),
}

export const policyApi = {
  quadrants: () => http.get<T.Quadrant[]>('/display-policy/quadrants'),
  tiers: () => http.get<{ tierNames: string[]; rows: T.TierRow[]; approver: string }>('/benchmark-tiers'),
  requestChange: (indicator: string, toTier: number, reason: string) =>
    http.post<T.TierRow>(`/benchmark-tiers/${encodeURIComponent(indicator)}/change-requests`, { toTier, reason }),
}

export const auditApi = {
  logs: (type: string, page: number) =>
    http.get<{ types: string[]; rows: T.AuditLog[]; total: number; page: number; size: number; todayCount: number; todayOverreach: number }>(
      '/audit/logs',
      { type, page, size: 10 },
    ),
  trace: (wm: string) => http.get<T.AuditTrace>('/audit/trace', { wm }),
}

export const exportApi = {
  options: (scope: string) =>
    http.get<{ content: string; purposes: string[]; validity: string[]; times: string[] }>('/exports/options', { scope }),
  request: (body: { scope: string; purpose: string; validity: string; times: string }) =>
    http.post<{ watermarkNo: string; content: string }>('/exports', body),
}
