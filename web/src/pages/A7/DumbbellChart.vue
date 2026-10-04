<script setup lang="ts">
import { computed } from 'vue'
import type { A7Dumbbell } from '@/mock/A7'

/** §05 标杆对比: dumbbell per metric, 标杆组 (green) vs 偏离组 (red), own scale per row. */
const props = defineProps<{ benchmarkLegend: string; deviantLegend: string; rows: A7Dumbbell[] }>()

const num = (n: number) => n.toLocaleString('zh-CN', { maximumFractionDigits: 1 })

const rows = computed(() =>
  props.rows.map(d => {
    const a = ((d.benchmark - d.min) / (d.max - d.min)) * 100
    const b = ((d.deviant - d.min) / (d.max - d.min)) * 100
    return { n: d.label, a: a.toFixed(1) + '%', b: b.toFixed(1) + '%', w: (b - a).toFixed(1) + '%', av: num(d.benchmark), bv: num(d.deviant), g: d.gap }
  }),
)
</script>

<template>
  <div class="flex gap-4 text-[11px] text-ink-4">
    <span class="flex items-center gap-[5px]"><span class="size-2.5 rounded-full bg-ok" />{{ benchmarkLegend }}</span>
    <span class="flex items-center gap-[5px]"><span class="size-2.5 rounded-full bg-bad" />{{ deviantLegend }}</span>
  </div>
  <div v-for="d in rows" :key="d.n" class="grid grid-cols-[140px_1fr_64px] items-center gap-3.5 py-1.5 text-xs">
    <span class="text-ink-3">{{ d.n }}</span>
    <div class="relative h-[26px]">
      <div class="absolute inset-x-0 top-3 h-0.5 bg-line-3" />
      <div class="absolute top-[11px] h-1 rounded-[2px] bg-[#F3B4AE]" :style="{ left: d.a, width: d.w }" />
      <span class="absolute top-1.5 -ml-[7px] size-3.5 rounded-full border-2 border-white bg-ok" :style="{ left: d.a }" />
      <span class="absolute top-1.5 -ml-[7px] size-3.5 rounded-full border-2 border-white bg-bad" :style="{ left: d.b }" />
      <span class="yb-num absolute -top-2 -translate-x-1/2 text-[11px] text-ok-ink" :style="{ left: d.a }">{{ d.av }}</span>
      <span class="yb-num absolute -top-2 -translate-x-1/2 text-[11px] text-bad-ink" :style="{ left: d.b }">{{ d.bv }}</span>
    </div>
    <span class="yb-num text-right font-semibold">{{ d.g }}</span>
  </div>
</template>
