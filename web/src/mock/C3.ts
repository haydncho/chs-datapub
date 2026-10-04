/** C3 外部监督 — seed data (same shape as GET /api/v1/pages/C3). Summary-only public layer. */

export type C3Tone = 'ok' | 'info'
export type C3Icon = 'coin' | 'shield' | 'doc' | 'check'

export interface C3Kpi {
  label: string
  /** value with optional unit after a space ("29.6 亿") */
  value: string
  sub: string
  tone: C3Tone
  icon: C3Icon
}

export type C3EventKind = 'monthly' | 'quarterly' | 'topic'

export interface C3CalendarEvent {
  /** label in the cell: 月告知 / 季度 / 专题 */
  label: string
  kind: C3EventKind
  /** already published (false = 计划中) */
  done: boolean
}

export interface C3Month {
  month: string
  events: C3CalendarEvent[]
}

export interface C3Feedback {
  label: string
  value: string
  /** progress bar 0–100 */
  pct: number
}

export interface C3Data {
  year: number
  kpis: C3Kpi[]
  calendar: C3Month[]
  feedback: C3Feedback[]
}

const months: C3Month[] = Array.from({ length: 12 }, (_, i) => ({
  month: String(i + 1),
  events: [
    { label: '月告知', kind: 'monthly' as const, done: i < 9 },
    ...([2, 5, 8, 11].includes(i) ? [{ label: '季度', kind: 'quarterly' as const, done: i < 9 }] : []),
    ...(i === 8 ? [{ label: '专题', kind: 'topic' as const, done: true }] : []),
  ],
}))

export const C3_SEED: C3Data = {
  year: 2026,
  kpis: [
    { label: '统筹基金支出 · 本年', value: '29.6 亿', sub: '同比 +5.8%', tone: 'info', icon: 'coin' },
    { label: 'DRG 覆盖病例', value: '97.9%', sub: '全市二级以上', tone: 'ok', icon: 'shield' },
    { label: '已公开发布', value: '46 期', sub: '按时率 98%', tone: 'info', icon: 'doc' },
    { label: '机构签收率', value: '96%', sub: '52 家定点机构', tone: 'ok', icon: 'check' },
  ],
  calendar: months,
  feedback: [
    { label: '机构意见答复率', value: '78%', pct: 78 },
    { label: '意见平均答复', value: '3.2 天', pct: 68 },
    { label: '引发报告更正', value: '2 次 · 全部完成', pct: 100 },
    { label: '预警回执率', value: '67%', pct: 67 },
  ],
}

/**
 * ACTIONS:
 * submitSuggestion({}) — 提交监督建议: records an oversight suggestion from the external supervisor; answered within 15 个工作日.
 */
