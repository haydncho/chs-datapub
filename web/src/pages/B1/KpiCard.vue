<script setup lang="ts">
import { computed } from 'vue'
import { A, AS, G, GS, GT, R, BRAND, BRAND_SOFT } from '@/lib/palette'
import type { B1Kpi } from '@/mock/B1'
import { PERCENTILE_HINT, WATCH_BELOW } from './percentile'

/** KPI card: gradient + icon, value with mini trend, MoM delta and peer-percentile bar. */
const props = defineProps<{ kpi: B1Kpi; icon: string }>()

const view = computed(() => {
  const k = props.kpi
  const up = k.mom > 0
  const good = k.goodDirection > 0 ? up : !up
  const warn = k.percentile < WATCH_BELOW
  const [g, bd, ic] = warn ? [AS, '#F6DFB8', A] : good ? [GS, '#CBEBDB', G] : [BRAND_SOFT, '#DCE6F8', BRAND]
  return {
    g, bd, ic,
    mc: warn ? A : BRAND,
    delta: (up ? '▲ ' : '▼ ') + Math.abs(k.mom),
    dc: good ? GT : R,
    spark: k.spark.map((h, j) => ({ h: h + 'px', c: j === k.spark.length - 1 ? BRAND : '#C7D7F7' })),
  }
})
</script>

<template>
  <div
    class="yb-stat relative flex flex-col gap-2 overflow-hidden rounded-[var(--radius-card)] border px-4 py-3.5"
    :style="{ '--stat-g': view.g, '--stat-bd': view.bd, '--stat-stop': '64%' }"
  >
    <svg
      viewBox="0 0 24 24" width="84" height="84" fill="none" :stroke="view.ic" stroke-width="1.2"
      stroke-linecap="round" stroke-linejoin="round"
      class="pointer-events-none absolute -top-[18px] -right-4 opacity-[.07]"
    ><path :d="icon" /></svg>
    <div class="relative flex items-center justify-between text-[12px]">
      <span class="flex items-center gap-[7px] whitespace-nowrap text-ink-4">
        <span
          class="flex size-6 items-center justify-center rounded-[7px] bg-white"
          :style="{ boxShadow: `0 0 0 1px ${view.bd}` }"
        >
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" :stroke="view.ic" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="icon" /></svg>
        </span>{{ kpi.name }}
      </span>
      <span class="yb-num font-semibold whitespace-nowrap" :style="{ color: view.dc }">{{ view.delta }}</span>
    </div>
    <div class="flex items-end justify-between gap-3">
      <div class="min-w-0 flex-1 whitespace-nowrap">
        <span class="yb-num text-[28px] leading-none font-semibold">{{ kpi.value }}</span>
        <span class="text-[11px] text-ink-4"> {{ kpi.unit }}</span>
      </div>
      <div class="flex h-6 shrink-0 items-end gap-0.5">
        <span v-for="(b, i) in view.spark" :key="i" class="w-1 rounded-[1px]" :style="{ height: b.h, background: b.c }" />
      </div>
    </div>
    <div class="relative h-2 rounded-[3px] bg-line-2">
      <div class="absolute inset-y-0 left-1/4 w-1/2 bg-[#DCE5F6]" />
      <div class="absolute -inset-y-0.5 left-1/2 border-l-[1.5px] border-ink-4" />
      <div
        class="absolute -top-1 -ml-0.5 h-4 w-1 rounded-[2px]"
        :style="{ left: kpi.percentile + '%', background: view.mc }"
      />
    </div>
    <div class="flex justify-between text-[11px]">
      <span class="text-ink-5" :title="PERCENTILE_HINT">同级分位 · 越高越好</span>
      <span class="yb-num font-semibold" :style="{ color: view.mc }">P{{ kpi.percentile }}</span>
    </div>
  </div>
</template>
