/** B5 意见核对 — seed data (same shape as GET /api/v1/pages/B5). */

import type { A10Item } from './A10'

export type B5Result = 'ok' | 'diff'

/** 佐证材料 metadata (the file itself stays with the institution) */
export interface B5Attachment {
  name: string
  size: number
  type: string
}

/** one answered item of a stored submission */
export interface B5Answer {
  id: string
  result: B5Result
  ownValue?: string
  reason?: string
  attachments?: B5Attachment[]
}

export interface B5Item {
  id: string
  /** label, e.g. "BR25 本院病例数" */
  label: string
  /** value in the draft, e.g. "286 例" */
  value: string
  /** optional cross-check reference shown in green ("HIS 统计 286 例") */
  reference: string
}

export interface B5Data {
  draft: string
  title: string
  subtitle: string
  /** calendar days until the deadline (0 once closed) — computed by the server */
  remainingDays: number
  footnote: string
  items: B5Item[]
  /** current 核对轮次 (server): deadline MM-DD HH:mm, closed once it has passed */
  round?: { id: string; deadline: string; closed: boolean }
  /** whether the viewing identity may submit now, and why not (医保局身份 / 已提交 / 已截止) */
  viewer?: { canSubmit: boolean; reason?: string; org?: string }
  /** this institution's stored submission for the round (a reload keeps 已提交) */
  submission?: { submittedAt: string; submittedBy: string; items: B5Answer[] }
  /** this institution's feedback items in A10 with status and the 医保局 reply (答复回流) */
  progress?: A10Item[]
}

export const B5_SEED: B5Data = {
  draft: '核对稿 · BR25 脑缺血性疾患专题 v3',
  title: '请确认本院相关数据',
  subtitle: '发布前核对 · 仅本院可见 · 截止 10-30 18:00',
  remainingDays: 2,
  footnote: '逾期未确认视为无异议 · 有差异的项将生成意见单进入医保局受理',
  items: [
    { id: 'cases', label: 'BR25 本院病例数', value: '286 例', reference: 'HIS 统计 286 例' },
    { id: 'fundDiff', label: 'BR25 例均基金差额', value: '+2,140 元', reference: '' },
    { id: 'comorbidity', label: 'BR25 合并症编码率', value: '74%', reference: '' },
    { id: 'los', label: 'BR25 平均住院日', value: '11.6 天', reference: '' },
  ],
}

/**
 * ACTIONS:
 * submitVerification({ items: { id: string, result: 'ok' | 'diff', ownValue?: string, reason?: string,
 *                                attachments?: { name: string, size: number, type: string }[] }[] })
 *   — 提交核对结果: every item of the draft exactly once; 'diff' needs ownValue and reason. Only the institution's
 *   own identity (not the 医保局), once per institution and round, before the deadline. Items with result 'diff'
 *   generate an 意见单 (纠错, titled with the item label) that enters A10 意见与申诉 受理; its progress and the
 *   reply come back in `progress`.
 */
