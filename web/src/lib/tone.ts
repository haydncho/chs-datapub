import type { Tone } from '@/api/types'

/**
 * 状态色语义（设计稿固定）：绿 = 结余 / 正常；橙 = 关注；红 = 逆差 / 预警；紫 = 核对期 / 意见；蓝 = 进行中。
 * 映射到睿衡设计令牌（浅色 / 深色两套）。
 */
export const toneText: Record<Tone, string> = {
  success: 'text-success',
  warning: 'text-warning',
  danger: 'text-danger',
  primary: 'text-primary',
  ai: 'text-ai',
  muted: 'text-ink-muted',
}

/** 浅底 + 深字 + 细描边的标签。 */
export const toneTag: Record<Tone, string> = {
  success: 'border-success-line bg-success-soft text-success-ink',
  warning: 'border-warning-line bg-warning-soft text-warning-ink',
  danger: 'border-danger-line bg-danger-soft text-danger-ink',
  primary: 'border-primary-line bg-primary-tint text-primary-ink',
  ai: 'border-ai-line bg-ai-soft text-ai-ink',
  muted: 'border-line bg-chip text-ink-muted',
}

export const toneVar: Record<Tone, string> = {
  success: 'var(--c-success)',
  warning: 'var(--c-warning)',
  danger: 'var(--c-danger)',
  primary: 'var(--c-primary)',
  ai: 'var(--c-ai)',
  muted: 'var(--c-text-muted)',
}

export const toneSoft: Record<Tone, string> = {
  success: 'var(--c-success-soft)',
  warning: 'var(--c-warning-soft)',
  danger: 'var(--c-danger-soft)',
  primary: 'var(--c-primary-tint)',
  ai: 'var(--c-ai-soft)',
  muted: 'var(--c-divider)',
}
