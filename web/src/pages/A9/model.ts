import type { A9Kind } from '@/mock/A9'

/** Visual spec per node kind: icon path, accent colour, soft background. */
export const KIND: Record<A9Kind, { icon: string; c: string; cb: string }> = {
  自动: { icon: 'M13 2L4 14h7l-1 8 9-12h-7z', c: '#64748B', cb: '#F1F5F9' },
  人工: { icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c0-4 3.6-7 8-7s8 3 8 7', c: 'var(--brand)', cb: 'var(--brand-soft)' },
  审批: { icon: 'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3zM8.5 12l2.5 2.5 4.5-5', c: '#D2362B', cb: '#FDECEA' },
  签收: { icon: 'M4 13l2-8h12l2 8M4 13v6h16v-6M4 13h4l2 3h4l2-3h4', c: '#0F9960', cb: '#E7F6EF' },
  可选: { icon: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 8v4M12 16h.01', c: '#98A2B3', cb: '#F4F6F9' },
  并行: { icon: 'M6 3v6a3 3 0 0 0 3 3h6a3 3 0 0 1 3 3v6M18 3v6a3 3 0 0 1-3 3', c: '#6941C6', cb: '#F2EEFB' },
}

export const PALETTE: { kind: A9Kind; label: string }[] = [
  { kind: '人工', label: '人工处理' },
  { kind: '审批', label: '审批节点' },
  { kind: '签收', label: '签收节点' },
  { kind: '自动', label: '自动节点' },
  { kind: '并行', label: '并行网关' },
]

/** Tailwind classes for a lane's avatar square. */
export const laneTone = (l: string) =>
  l === '召集人' ? 'bg-bad-soft text-bad'
    : l === '定点医疗机构' ? 'bg-ok-soft text-ok-ink'
      : l === '专家组' ? 'bg-violet-soft text-violet'
        : 'bg-brand-soft text-brand'

/** Selectable chip (role / timeout / channel). */
export const chipCls = (on: boolean) =>
  on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3'

export const COLS = 6
export const LANE_H = 96
export const CW = 100 / COLS

/** A node with its effective (edited) lane and duration. */
export interface FlowNode {
  idx: number
  lane: string
  col: number
  name: string
  kind: A9Kind
  days: number
}

export interface ColSpan {
  has: boolean
  start: number
  end: number
}
