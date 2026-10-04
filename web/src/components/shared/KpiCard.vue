<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { Tone } from '@/api/types'
import StrokeIcon from '@/components/ui/stroke-icon/StrokeIcon.vue'
import { iconPaths } from '@/lib/icons'
import { toneVar } from '@/lib/tone'

/**
 * KPI 卡:对角渐变底 + 光晕 + 渐变图标块 + 大号数字 + 同比箭头 + 迷你柱图(数据来自真实序列时才传 spark)。
 * delta:{ text, dir } dir = up / down / flat;good 指明“升”是否为好事(决定红绿)。
 */
const props = withDefaults(
  defineProps<{
    label: string
    value: string
    unit?: string
    desc?: string
    tone?: Tone
    icon?: string
    small?: boolean
    delta?: { text: string; dir: 'up' | 'down' | 'flat'; good?: boolean }
    spark?: number[]
    /** 迷你图形式:柱 / 折线 */
    sparkType?: 'bar' | 'line'
    /** 数字滚动入场(取 value 中第一个数字) */
    animate?: boolean
  }>(),
  { tone: 'primary', icon: 'pct', sparkType: 'bar' },
)

const shown = ref(props.value)
let raf = 0
const NUM = /-?\d[\d,]*\.?\d*/
function run() {
  cancelAnimationFrame(raf)
  const m = props.value.match(NUM)
  if (!props.animate || !m || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    shown.value = props.value
    return
  }
  const target = parseFloat(m[0].replace(/,/g, ''))
  const dec = m[0].includes('.') ? m[0].split('.')[1].length : 0
  const comma = m[0].includes(',')
  const t0 = performance.now()
  const tick = (t: number) => {
    const k = Math.min(1, (t - t0) / 900)
    const e = 1 - Math.pow(1 - k, 3)
    const n = target * e
    let txt = n.toFixed(dec)
    if (comma) txt = Number(txt).toLocaleString('en-US', { minimumFractionDigits: dec, maximumFractionDigits: dec })
    shown.value = props.value.replace(NUM, txt)
    if (k < 1) raf = requestAnimationFrame(tick)
    else shown.value = props.value
  }
  raf = requestAnimationFrame(tick)
}
watch(() => props.value, run, { immediate: true })
onBeforeUnmount(() => cancelAnimationFrame(raf))

const color = computed(() => (props.tone === 'muted' ? 'var(--c-text-sub)' : toneVar[props.tone]))
const deltaCls = computed(() => {
  if (!props.delta || props.delta.dir === 'flat') return 'text-ink-muted'
  const good = props.delta.good ?? true
  return (props.delta.dir === 'up') === good ? 'text-success' : 'text-danger'
})

const W = 72
const H = 30
const bars = computed(() => {
  const s = props.spark ?? []
  if (!s.length) return []
  const max = Math.max(...s, 1e-9)
  const w = W / s.length
  return s.map((v, i) => {
    const h = Math.max(3, (Math.abs(v) / max) * (H - 4))
    return { x: i * w + w * 0.18, y: H - h, w: w * 0.64, h, o: 0.35 + 0.65 * ((i + 1) / s.length) }
  })
})
const line = computed(() => {
  const s = props.spark ?? []
  if (s.length < 2) return ''
  const max = Math.max(...s)
  const min = Math.min(...s)
  const span = max - min || 1
  return s.map((v, i) => `${i ? 'L' : 'M'}${(i / (s.length - 1)) * W} ${H - 4 - ((v - min) / span) * (H - 10)}`).join(' ')
})
</script>

<template>
  <div class="kpi relative overflow-hidden border border-line-strong bg-surface px-5 pt-4 pb-3.5" :style="{ '--k': color }">
    <span class="absolute inset-x-0 top-0 h-[3px]" :style="{ background: color }" />
    <div class="flex items-baseline justify-between gap-2">
      <span class="truncate text-[12px] leading-[1.6] tracking-[.08em] text-ink-muted">{{ label }}</span>
      <span class="font-display text-[11px] text-ink-faint"><StrokeIcon :d="iconPaths[icon]" :size="13" /></span>
    </div>
    <div class="mt-1 flex items-baseline gap-1.5 whitespace-nowrap">
      <span class="font-display leading-[1.1] font-bold tracking-tight tabular-nums text-ink" :class="small ? 'text-[22px]' : 'text-[34px]'">{{ shown }}</span>
      <span v-if="unit" class="text-[12px] text-ink-faint">{{ unit }}</span>
    </div>
    <div v-if="delta || desc || bars.length || line" class="mt-2 flex items-end gap-x-2 border-t border-dashed border-line pt-2 text-[12px] leading-[1.6]">
      <div class="flex min-w-0 flex-1 flex-wrap items-center gap-x-2">
        <span v-if="delta" class="inline-flex items-center gap-0.5 font-medium" :class="deltaCls">
          <svg v-if="delta.dir !== 'flat'" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path :d="delta.dir === 'up' ? 'M12 19V5M5 12l7-7 7 7' : 'M12 5v14M5 12l7 7 7-7'" /></svg>
          {{ delta.text }}
        </span>
        <span v-if="desc" class="text-ink-muted"><slot name="desc">{{ desc }}</slot></span>
      </div>
      <svg v-if="bars.length || line" :width="W" :height="H" class="ml-auto flex-none" aria-hidden="true">
        <template v-if="sparkType === 'bar'">
          <rect v-for="(b, i) in bars" :key="i" :x="b.x" :y="b.y" :width="b.w" :height="b.h" :fill="color" :fill-opacity="b.o" />
        </template>
        <path v-else :d="line" fill="none" :stroke="color" stroke-width="1.8" stroke-linecap="square" stroke-linejoin="miter" />
      </svg>
    </div>
  </div>
</template>

