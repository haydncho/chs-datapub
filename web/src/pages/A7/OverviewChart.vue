<script setup lang="ts">
import { cn } from '@/lib/utils'
import type { A7Kpi, A7Tier } from '@/mock/A7'
import { TIER_C } from './colors'

/** §01 整体描述: four KPI tiles + 100% stacked bar of cases by institution tier. */
defineProps<{ kpis: A7Kpi[]; tierTitle: string; tiers: A7Tier[] }>()
</script>

<template>
  <div class="grid grid-cols-4 gap-2.5">
    <div
      v-for="k in kpis"
      :key="k.label"
      :class="cn('rounded-[10px] px-3.5 py-3', k.tone === 'bad' ? 'bg-bad-soft' : 'bg-surface-1')"
    >
      <div class="text-[11px] text-ink-4">{{ k.label }}</div>
      <div :class="cn('yb-num text-2xl font-semibold', k.tone === 'bad' && 'text-bad')">{{ k.value }}</div>
      <div :class="cn('text-[11px]', k.subBad ? 'text-bad' : 'text-ink-3')">{{ k.sub }}</div>
    </div>
  </div>
  <div>
    <div class="mb-1.5 text-xs text-ink-4">{{ tierTitle }}</div>
    <div class="flex h-6 overflow-hidden rounded-md">
      <span
        v-for="(t, i) in tiers"
        :key="t.name"
        class="flex items-center overflow-hidden pl-2 text-[11px] whitespace-nowrap text-white"
        :style="{ width: t.pct + '%', background: TIER_C[i % TIER_C.length] }"
        :title="`${t.name} ${t.pct}%`"
      >{{ t.pct < 9 ? '' : t.name + ' ' }}{{ t.pct }}%</span>
    </div>
  </div>
</template>
