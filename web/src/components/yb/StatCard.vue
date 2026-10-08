<script setup lang="ts">
import { computed } from 'vue'
import { splitUnit } from '@/lib/format'
import { cn } from '@/lib/utils'

/**
 * KPI card with soft gradient background and a small icon, toned by status
 * (A3 / B1 / B2 / B7 / C3 summary rows). Background follows 外观配置 卡片样式
 * (渐变 by default; 描边 / 投影 render it flat — see `.yb-stat` in style.css).
 */
export type StatTone = 'ok' | 'warn' | 'bad' | 'info'
/** value size: sm 20px (B7), md 24px (default, Pad 22), lg 26px (C3, Pad 24) — 与 KpiValue 同一档位 */
export type StatSize = 'sm' | 'md' | 'lg'
const props = withDefaults(defineProps<{
  label: string
  value: string | number
  /** unit rendered smaller; if omitted it is split from `value` ("1,284 万") */
  unit?: string
  sub?: string
  /** colour of the sub line (CSS colour) */
  subColor?: string
  tone?: StatTone
  size?: StatSize
  /** 24×24 SVG path data for the icon */
  icon?: string
  class?: string
}>(), { tone: 'info', size: 'md' })

const TONES: Record<StatTone, { g: string; bd: string; ic: string; ib: string }> = {
  ok: { g: '#E7F6EF', bd: '#CBEBDB', ic: '#0F9960', ib: '#fff' },
  warn: { g: '#FEF3E2', bd: '#F6DFB8', ic: '#E8890C', ib: '#fff' },
  bad: { g: '#FDECEA', bd: '#F5CFCB', ic: '#D2362B', ib: '#fff' },
  info: { g: 'var(--brand-soft)', bd: '#DCE6F8', ic: 'var(--brand)', ib: '#fff' },
}
const VALUE_SIZE: Record<StatSize, string> = {
  sm: 'text-xl leading-[26px]',
  md: 'text-2xl leading-[30px] max-xl:text-[22px] max-xl:leading-[28px]',
  lg: 'text-[26px] leading-[32px] max-xl:text-2xl max-xl:leading-[30px]',
}
const t = computed(() => TONES[props.tone])
const parts = computed(() => (props.unit != null ? { vn: String(props.value), vu: props.unit } : splitUnit(props.value)))
</script>

<template>
  <div
    :class="cn('yb-stat relative isolate flex items-start gap-3.5 overflow-hidden rounded-[var(--radius-card)] border px-card-x py-card-y-sm', props.class)"
    :style="{ '--stat-g': t.g, '--stat-bd': t.bd }"
  >
    <!-- 文字列压在装饰图形之上;说明行可伸到图标下方(图标只占上半部),整卡放不下时省略而不是被裁切 -->
    <div class="relative z-[1] min-w-0 flex-1 whitespace-nowrap">
      <div class="truncate text-xs text-ink-4" :title="label">{{ label }}</div>
      <div :class="['yb-kpi-v yb-num font-semibold', VALUE_SIZE[size]]">
        {{ parts.vn }}<span v-if="parts.vu" class="ml-[3px] text-[13px] font-medium text-ink-4">{{ parts.vu }}</span>
      </div>
      <div v-if="sub || $slots.sub" :class="['truncate text-xs', icon && 'mr-[-50px]']" :style="{ color: subColor ?? 'var(--ink-3)' }"><slot name="sub">{{ sub }}</slot></div>
    </div>
    <template v-if="icon">
      <span class="relative z-[1] flex size-9 shrink-0 items-center justify-center rounded-[10px]" :style="{ background: t.ib }">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" :stroke="t.ic" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="icon" /></svg>
      </span>
      <svg viewBox="0 0 24 24" width="96" height="96" fill="none" :stroke="t.ic" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" class="pointer-events-none absolute -right-[18px] -bottom-[26px] opacity-[.07]"><path :d="icon" /></svg>
    </template>
  </div>
</template>
