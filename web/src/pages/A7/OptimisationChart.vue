<script setup lang="ts">
import type { A7OptPart } from '@/mock/A7'
import { BRAND } from '@/lib/palette'

/** §06 优化空间: disclaimer banner + annualised total + composition bar (legend below, so nothing is clipped). */
defineProps<{ notice: string; label: string; total: string; unit: string; parts: A7OptPart[] }>()

const PART_C = ['#5B8FD9', '#9DBCE9', BRAND]
const PART_INK = ['#fff', '#0F1A2E', '#fff']
</script>

<template>
  <div class="rounded-[10px] bg-warn-soft px-3.5 py-2.5 text-xs font-medium text-[#7A4510]">{{ notice }}</div>
  <div class="flex items-end gap-7 max-sm:flex-col max-sm:items-stretch max-sm:gap-3">
    <div class="shrink-0">
      <div class="text-xs text-ink-4">{{ label }}</div>
      <div class="yb-num text-[44px] leading-[1.1] font-semibold whitespace-nowrap">
        {{ total }}<span class="font-sans text-base text-ink-4"> {{ unit }}</span>
      </div>
    </div>
    <div class="flex min-w-0 flex-1 flex-col gap-1.5">
      <div class="flex h-7 overflow-hidden rounded-md text-[11px]" role="img" :aria-label="parts.map(p => `${p.name} ${p.value} ${unit}(${p.pct}%)`).join(',')">
        <span
          v-for="(p, i) in parts"
          :key="p.name"
          class="yb-num flex items-center justify-center"
          :style="{ width: p.pct + '%', background: PART_C[i % 3], color: PART_INK[i % 3] }"
        >{{ p.pct }}%</span>
      </div>
      <div class="flex flex-wrap gap-x-3.5 gap-y-1 text-[11px] text-ink-3">
        <span v-for="(p, i) in parts" :key="p.name" class="flex items-center gap-1 whitespace-nowrap">
          <span class="size-[9px] rounded-[2px]" :style="{ background: PART_C[i % 3] }" />{{ p.name }} <b class="yb-num font-semibold text-ink-1">{{ p.value }}</b> {{ unit }}
        </span>
      </div>
    </div>
  </div>
</template>
