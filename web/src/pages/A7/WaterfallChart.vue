<script setup lang="ts">
import { computed } from 'vue'
import { A, R } from '@/lib/palette'
import type { A7WaterfallBar, A7WaterfallKind } from '@/mock/A7'
import { pctStr } from './colors'

/** §04 差异归因: waterfall 全市均值 → +患者差异 → +行为差异 → 偏离组均值. */
const props = defineProps<{ scaleMax: number; bars: A7WaterfallBar[]; note: string }>()

const KIND_C: Record<A7WaterfallKind, string> = { city: '#98A2B3', patient: '#9DBCE9', behaviour: A, deviant: R }

const wf = computed(() =>
  props.bars.map(x => ({
    label: x.label,
    display: x.display,
    c: KIND_C[x.kind],
    b: pctStr((x.base / props.scaleMax) * 100),
    h: pctStr((x.value / props.scaleMax) * 100),
    top: pctStr(((x.base + x.value) / props.scaleMax) * 100),
  })),
)
</script>

<template>
  <div class="relative grid h-[220px] grid-cols-4 gap-8 border-b border-line-4 px-6 max-sm:gap-2.5 max-sm:px-0">
    <div v-for="w in wf" :key="w.label" class="relative">
      <div class="absolute inset-x-0 rounded" :style="{ bottom: w.b, height: w.h, background: w.c }" />
      <div class="yb-num absolute inset-x-0 text-center text-[15px] font-semibold whitespace-nowrap max-sm:text-[13px]" :style="{ bottom: `calc(${w.top} + 6px)` }">{{ w.display }}</div>
    </div>
  </div>
  <div class="grid grid-cols-4 gap-8 px-6 text-center text-xs text-ink-3 max-sm:gap-2.5 max-sm:px-0">
    <span v-for="w in wf" :key="w.label">{{ w.label }}</span>
  </div>
  <div class="text-[11px] text-ink-4">{{ note }}</div>
</template>
