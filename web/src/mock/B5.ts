/** B5 意见核对 — seed data (same shape as GET /api/v1/pages/B5). */

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
  remainingDays: number
  footnote: string
  items: B5Item[]
}

export const B5_SEED: B5Data = {
  draft: '核对稿 · BR25 脑缺血性疾患专题 v3',
  title: '请确认本院相关数据',
  subtitle: '发布前核对 · 仅本院可见 · 截止 10-06 18:00',
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
 * submitVerification({ items: { id: string, result: 'ok' | 'diff', ownValue?: string, reason?: string }[] })
 *   — 提交核对结果 (only when all 4 items are confirmed). Items with result 'diff' generate an
 *   意见单 that enters A10 意见与申诉 受理; all 'ok' means 已确认全部数据一致.
 */
