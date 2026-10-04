import type { Tone } from '@/api/types'

/** 指标状态 → 状态色：已上线 绿 / 审批中 蓝 / 草稿 灰 / 本期暂缓 橙。 */
export const statusTone = (s: string): Tone =>
  s === '已上线' ? 'success' : s === '审批中' ? 'primary' : s === '本期暂缓' ? 'warning' : 'muted'

/** 标签：国家底稿必选 蓝 / 地方增选 灰 / 仅内部 灰。 */
export const tagTone = (t: string): Tone => (t === '必选' ? 'primary' : 'muted')

/** 分组文字色：钱 蓝 / 效 绿 / 错 红。 */
export const grpClass = (g: string) => (g === '钱' ? 'text-primary' : g === '效' ? 'text-success' : 'text-danger')

export const fmt = (n: number | null | undefined, digits = 0) =>
  n == null ? '—' : n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
