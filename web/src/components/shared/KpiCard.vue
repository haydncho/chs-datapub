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
const uid = `k${Math.random().toString(36).slice(2, 8)}`
</script>

<template>
  <div class="kpi group relative overflow-hidden rounded-2xl border border-line-soft bg-surface px-5 pt-4 pb-4 shadow-card" :style="{ '--k': color }">
    <div class="kpi-glow pointer-events-none absolute -top-10 -right-10 size-32 rounded-full" />
    <div class="relative flex items-start gap-3">
      <span class="kpi-chip flex size-10 flex-none items-center justify-center rounded-xl text-white">
        <StrokeIcon :d="iconPaths[icon]" :size="19" />
      </span>
      <div class="min-w-0 flex-1">
        <div class="truncate text-[12px] leading-[1.6] text-ink-muted">{{ label }}</div>
        <div class="mt-0.5 flex items-baseline gap-1.5 whitespace-nowrap">
          <span class="leading-[1.1] font-bold tracking-tight tabular-nums" :class="small ? 'text-[19px]' : 'text-[27px]'">{{ shown }}</span>
          <span v-if="unit" class="text-[12px] text-ink-faint">{{ unit }}</span>
        </div>
      </div>
    </div>
    <div v-if="delta || desc || bars.length || line" class="relative mt-2.5 flex items-end gap-x-2 text-[12px] leading-[1.6]">
      <div class="flex min-w-0 flex-1 flex-wrap items-center gap-x-2">
      <span v-if="delta" class="inline-flex items-center gap-0.5 font-medium" :class="deltaCls">
        <svg v-if="delta.dir !== 'flat'" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path :d="delta.dir === 'up' ? 'M12 19V5M5 12l7-7 7 7' : 'M12 5v14M5 12l7 7 7-7'" /></svg>
        {{ delta.text }}
      </span>
      <span v-if="desc" class="text-ink-muted"><slot name="desc">{{ desc }}</slot></span>
      </div>
      <svg v-if="bars.length || line" :width="W" :height="H" class="ml-auto flex-none" aria-hidden="true">
        <defs>
          <linearGradient :id="uid" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" :stop-color="color" stop-opacity="0.9" />
            <stop offset="1" :stop-color="color" stop-opacity="0.25" />
          </linearGradient>
        </defs>
        <template v-if="sparkType === 'bar'">
          <rect v-for="(b, i) in bars" :key="i" :x="b.x" :y="b.y" :width="b.w" :height="b.h" rx="1.5" :fill="`url(#${uid})`" :fill-opacity="b.o" />
        </template>
        <template v-else>
          <path :d="line" fill="none" :stroke="color" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </template>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.kpi {
  background-image: linear-gradient(135deg, color-mix(in srgb, var(--k) 9%, var(--c-surface)) 0%, var(--c-surface) 62%);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.kpi:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px color-mix(in srgb, var(--k) 16%, transparent);
}
.kpi-glow {
  background: radial-gradient(closest-side, color-mix(in srgb, var(--k) 22%, transparent), transparent);
}
.kpi-chip {
  background: linear-gradient(135deg, color-mix(in srgb, var(--k) 62%, #fff) 0%, var(--k) 100%);
  box-shadow: 0 6px 14px color-mix(in srgb, var(--k) 32%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.35);
}
</style>
