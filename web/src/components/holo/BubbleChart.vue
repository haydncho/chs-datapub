<script setup lang="ts">
import { useElementSize } from '@vueuse/core'
import { computed, ref } from 'vue'
import type { HoloAxis } from '@/api/holo'

/**
 * 病组气泡图（SVG 自绘，A2 / B1 共用）。编码规则（交接说明“图表规范”）：
 * 横轴病例数；纵轴例均基金差额，零线为虚线“收支平衡线”，上半浅红“↑ 逆差”、下半浅绿“↓ 结余”；
 * 半径由引擎给出；颜色、外环、标注由调用方按图层给出。颜色一律取语义令牌（深色主题自动切换）。
 */
export interface ChartBubble {
  id: string
  cases: number
  diff: number
  r: number
  fill: string
  opacity?: number
  /** 图层外环颜色（白环 2px + 色环 2px）。 */
  ring?: string | null
  /** 常显标注（关键少数病组：“编码 简称”）。 */
  label?: string
  tip: string
  dashed?: boolean
}

const props = withDefaults(
  defineProps<{
    axis: HoloAxis
    bubbles: ChartBubble[]
    /** 灰色背景气泡（不可交互，如全市同级同组均值）。 */
    background?: { cases: number; diff: number; r: number; tip?: string }[]
    selected?: string | null
    height?: number
    xTitle: string
    yTitle?: string
  }>(),
  { background: () => [], selected: null, height: 430, yTitle: '例均基金差额(元)' },
)
const emit = defineEmits<{ select: [id: string] }>()

const box = ref<HTMLElement | null>(null)
const { width } = useElementSize(box)
const M = { l: 66, r: 14, t: 12, b: 36 }
const W = computed(() => Math.max(width.value, 320))
const pw = computed(() => W.value - M.l - M.r)
const ph = computed(() => props.height - M.t - M.b)
const px = (cases: number) => M.l + (Math.min(cases, props.axis.xMax) / props.axis.xMax) * pw.value
const py = (diff: number) => {
  const y = props.axis.yMax
  return M.t + ((y - Math.max(-y, Math.min(y, diff))) / (2 * y)) * ph.value
}

/** 绘制顺序：大泡在下、小泡在上，选中项最上。 */
const ordered = computed(() =>
  [...props.bubbles].sort((a, b) => (a.id === props.selected ? 1 : b.id === props.selected ? -1 : b.r - a.r)),
)

/** 文本宽度估算（11px，CJK 全宽）。 */
const textW = (s: string) => [...s].reduce((w, c) => w + (/[\u0000-ÿ]/.test(c) ? 6.4 : 11), 0) + 8

/** 标注：右侧 70% 以外的气泡标在左侧；与已放置标注重叠时上下错开。 */
const labels = computed(() => {
  const placed: { x: number; y: number; w: number; h: number; text: string }[] = []
  const items = props.bubbles.filter((b) => b.label).sort((a, b) => py(a.diff) - py(b.diff))
  for (const b of items) {
    const cx = px(b.cases)
    const cy = py(b.diff)
    const w = textW(b.label!)
    const left = cx > M.l + pw.value * 0.7
    const x = left ? cx - b.r - 4 - w : cx + b.r + 4
    const h = 16
    let y = cy - 9
    for (const off of [0, -14, 14, -26, 26, -38, 38]) {
      const yy = cy - 9 + off
      const hit = placed.some((p) => x < p.x + p.w && p.x < x + w && yy < p.y + p.h && p.y < yy + h)
      if (!hit) {
        y = yy
        break
      }
    }
    placed.push({ x, y, w, h, text: b.label! })
  }
  return placed
})

function onKey(e: KeyboardEvent, id: string) {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    emit('select', id)
  }
}
</script>

<template>
  <div ref="box" class="relative w-full" :style="{ height: `${height}px` }">
    <svg v-if="width" :width="W" :height="height" class="block select-none" role="img" :aria-label="`气泡图:${xTitle},${yTitle}`">
      <!-- 逆差 / 结余半区 -->
      <rect :x="M.l" :y="M.t" :width="pw" :height="ph / 2" fill="var(--c-danger-soft)" />
      <rect :x="M.l" :y="M.t + ph / 2" :width="pw" :height="ph / 2" fill="var(--c-success-soft)" />
      <!-- 网格与刻度 -->
      <g font-size="11" fill="var(--c-text-muted)">
        <template v-for="t in axis.yTicks" :key="`y${t.value}`">
          <line :x1="M.l" :x2="M.l + pw" :y1="py(t.value)" :y2="py(t.value)" stroke="var(--c-border-soft)" />
          <text :x="M.l - 8" :y="py(t.value) + 4" text-anchor="end" class="tabular-nums">{{ t.label }}</text>
        </template>
        <template v-for="(t, i) in axis.xTicks" :key="`x${t.value}`">
          <line :x1="M.l + (i / (axis.xTicks.length - 1)) * pw" :x2="M.l + (i / (axis.xTicks.length - 1)) * pw" :y1="M.t" :y2="M.t + ph" stroke="var(--c-divider)" />
          <text :x="M.l + (i / (axis.xTicks.length - 1)) * pw" :y="M.t + ph + 18" text-anchor="middle" class="tabular-nums">{{ t.label }}</text>
        </template>
      </g>
      <line :x1="M.l" :x2="M.l + pw" :y1="py(0)" :y2="py(0)" stroke="var(--c-text-sub)" stroke-width="1.5" stroke-dasharray="5 4" />
      <text :x="M.l + pw - 6" :y="py(0) - 8" text-anchor="end" font-size="11" font-weight="500" fill="var(--c-text-sub)">收支平衡线</text>
      <text :x="M.l + 8" :y="M.t + 16" font-size="11" font-weight="500" fill="var(--c-danger)">↑ 逆差</text>
      <text :x="M.l + 8" :y="M.t + ph - 8" font-size="11" font-weight="500" fill="var(--c-success)">↓ 结余</text>
      <text :transform="`translate(11 ${M.t + ph / 2}) rotate(-90)`" text-anchor="middle" font-size="11" fill="var(--c-text-sub)">{{ yTitle }}</text>

      <!-- 灰色背景 -->
      <circle v-for="(b, i) in background" :key="`bg${i}`" :cx="px(b.cases)" :cy="py(b.diff)" :r="b.r" fill="var(--c-text-ghost)" fill-opacity="0.5" pointer-events="none">
        <title v-if="b.tip">{{ b.tip }}</title>
      </circle>

      <!-- 气泡 -->
      <g
        v-for="b in ordered"
        :key="b.id"
        class="cursor-pointer outline-none"
        role="button"
        tabindex="0"
        :aria-label="b.tip"
        :aria-pressed="b.id === selected"
        :data-code="b.id"
        @click="emit('select', b.id)"
        @keydown="onKey($event, b.id)"
      >
        <title>{{ b.tip }}</title>
        <template v-if="b.ring">
          <circle :cx="px(b.cases)" :cy="py(b.diff)" :r="b.r + 3" fill="none" :stroke="b.ring" stroke-width="2" data-ring />
          <circle :cx="px(b.cases)" :cy="py(b.diff)" :r="b.r + 1" fill="none" stroke="var(--c-halo)" stroke-width="2" />
        </template>
        <circle
          :cx="px(b.cases)"
          :cy="py(b.diff)"
          :r="b.r"
          :fill="b.fill"
          :fill-opacity="b.opacity ?? 0.9"
          :stroke="b.id === selected ? 'var(--c-text)' : b.dashed ? 'var(--c-text-muted)' : 'var(--c-halo)'"
          :stroke-width="b.id === selected ? 2 : 1"
          :stroke-dasharray="b.dashed ? '3 2' : undefined"
          class="bubble"
        />
      </g>

      <!-- 关键少数标注 -->
      <g pointer-events="none" font-size="11" font-weight="600">
        <template v-for="l in labels" :key="l.text">
          <rect :x="l.x" :y="l.y" :width="l.w" :height="l.h" rx="3" fill="var(--c-surface)" fill-opacity="0.88" />
          <text :x="l.x + 4" :y="l.y + 12" fill="var(--c-text)">{{ l.text }}</text>
        </template>
      </g>
    </svg>
    <div class="pointer-events-none absolute right-[14px] bottom-0 text-[11px] text-ink-muted">{{ xTitle }} →</div>
  </div>
</template>

<style scoped>
.bubble {
  transition: stroke-width 0.1s;
}
g[role='button']:hover .bubble,
g[role='button']:focus-visible .bubble {
  stroke: var(--c-text);
  stroke-width: 1.5;
}
</style>
