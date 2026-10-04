import type { A4Group, A4Indicator, A4Source, A4Status, A4Tier } from '@/mock/A4'
import { BRAND, R, V } from '@/lib/palette'

export const TIER_LABEL: Record<A4Tier, string> = { pct: '匿名分位', anon: '匿名编号', named: '具名PK与排行', none: '—' }

/** status pill: label + Badge variant */
export const STATUS: Record<A4Status, { label: string; variant: 'ok' | 'brand' | 'muted' | 'warn' }> = {
  on: { label: '已上线', variant: 'ok' },
  review: { label: '审批中', variant: 'brand' },
  draft: { label: '草稿', variant: 'muted' },
  hold: { label: '本期暂缓', variant: 'warn' },
}

export const GROUPS: A4Group[] = ['钱', '效', '错']
export const GROUP_NAME: Record<A4Group, string> = { 钱: '钱 · 基金运行', 效: '效 · 支付效率', 错: '错 · 质量监管' }
export const GROUP_COLOR: Record<A4Group, string> = { 钱: BRAND, 效: V, 错: R }

/** 指标来源 filter chips (label → source) */
export const SOURCE_FILTERS: { label: string; source: A4Source | null }[] = [
  { label: '全部', source: null },
  { label: '国家底稿', source: 'national' },
  { label: '地方增选', source: 'local' },
  { label: '仅内部', source: 'internal' },
]
/** row tag text */
export const SOURCE_TAG: Record<A4Source, string> = { national: '国家底稿', local: '地方增选', internal: '仅内部' }
/** raw 来源 wording used in the detail panel (必选 / 增选 / 仅内部) */
export const SOURCE_RAW: Record<A4Source, string> = { national: '必选', local: '增选', internal: '仅内部' }

/** sortable columns: label + field (column index = position) */
export const COLUMNS: { label: string; key: keyof A4Indicator }[] = [
  { label: '指标', key: 'name' },
  { label: '分组', key: 'group' },
  { label: '对标档位', key: 'tier' },
  { label: '频次', key: 'freq' },
  { label: '版本', key: 'version' },
  { label: '被引用', key: 'refs' },
  { label: '状态', key: 'status' },
]
/** default widths (px) of columns 1–6 */
export const DEFAULT_WIDTHS: Record<number, number> = { 1: 44, 2: 110, 3: 40, 4: 52, 5: 56, 6: 76 }
/** columns the user may hide via 列与密度 */
export const HIDEABLE: [number, string][] = [[2, '对标档位'], [3, '频次'], [4, '版本'], [5, '被引用']]
export const DENSITY = ['紧凑', '标准', '宽松'] as const
export const DENSITY_PAD = ['7px', '11px', '15px'] as const

export interface A4Row {
  /** index into data.indicators */
  i: number
  ind: A4Indicator
  intl: boolean
  checked: boolean
  selected: boolean
  pending: A4Tier | null
}
