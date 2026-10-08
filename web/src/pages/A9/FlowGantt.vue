<script setup lang="ts">
import { computed } from 'vue'
import { pad } from '@/lib/format'
import { KIND, type ColSpan, type FlowNode } from './model'

const props = defineProps<{
  nodes: FlowNode[]
  cols: ColSpan[]
  total: number
  limit: number
  selected: number
}>()

// prototype: max(limit, total) × 1.08; also fit optional (不计入) bars so they never spill out of the card
const scale = computed(() => {
  const ends = props.nodes.map(n => (props.cols[n.col]?.start ?? 0) + n.days)
  return Math.max(props.limit, props.total, ...ends) * 1.08
})
const over = computed(() => props.total > props.limit)

const rows = computed(() =>
  props.nodes.map(n => {
    const st = props.cols[n.col]?.start ?? 0
    const opt = n.kind === '可选'
    const l = ((st / scale.value) * 100).toFixed(2) + '%'
    const w = ((Math.max(n.days, 0.15) / scale.value) * 100).toFixed(2) + '%'
    // a label after a bar that ends near the right edge would be clipped: put it before the bar instead
    const flip = (st + Math.max(n.days, 0.15)) / scale.value > 0.8 && st / scale.value > 0.2
    return {
      key: n.idx,
      n: pad(n.idx + 1) + ' ' + n.name,
      l,
      w,
      c: KIND[n.kind].c,
      op: opt ? 0.25 : n.idx === props.selected ? 1 : 0.55,
      t: opt ? n.days + 'd · 不计入' : n.days ? n.days + 'd' : '即时',
      pos: flip ? { right: `calc(100% - ${l} + 6px)` } : { left: `calc(${l} + ${w} + 6px)` },
    }
  }),
)
const ticks = computed(() => [0, 5, 10, 15, 20].filter(t => t <= scale.value).map(t => 'D' + t))
const limX = computed(() => (props.limit / scale.value).toFixed(4))
</script>

<template>
  <div class="rounded-[var(--radius-card)] border border-line-1 bg-white px-5 py-4 max-xl:pb-5">
    <div class="mb-3 flex items-baseline justify-between gap-3">
      <span class="text-[15px] font-semibold">时限甘特</span>
      <span class="text-xs whitespace-nowrap text-ink-4">
        关键路径 <b :class="['yb-num text-[15px]', over ? 'text-bad' : 'text-ok-ink']">{{ total }}</b> / {{ limit }} 个工作日
      </span>
    </div>
    <div class="max-xl:overflow-x-auto"><div class="relative flex flex-col gap-1.5 max-xl:min-w-[560px]">
      <div
        class="absolute -top-1 bottom-[18px] z-[2] border-l-[1.5px] border-dashed border-bad"
        :style="{ left: `calc(132px + (100% - 132px) * ${limX})` }"
      >
        <span class="absolute -top-3.5 left-1 text-[10px] whitespace-nowrap text-bad">法定期限</span>
      </div>
      <div v-for="g in rows" :key="g.key" class="grid h-[22px] grid-cols-[132px_minmax(0,1fr)] items-center">
        <span class="truncate pr-2.5 text-xs text-ink-3">{{ g.n }}</span>
        <div class="relative h-full rounded bg-surface-2">
          <div class="absolute inset-y-[3px] rounded-[3px]" :style="{ left: g.l, width: g.w, background: g.c, opacity: g.op }" />
          <span class="yb-num absolute top-0.5 text-[11px] whitespace-nowrap text-ink-4" :style="g.pos">{{ g.t }}</span>
        </div>
      </div>
      <div class="grid grid-cols-[132px_minmax(0,1fr)]">
        <span />
        <div class="yb-num flex justify-between text-[10px] text-ink-5">
          <span v-for="t in ticks" :key="t">{{ t }}</span>
        </div>
      </div>
    </div>
    </div>
  </div>
</template>
