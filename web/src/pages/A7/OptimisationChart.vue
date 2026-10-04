<script setup lang="ts">
import type { A7OptPart } from '@/mock/A7'
import { BRAND } from '@/lib/palette'

/** §06 优化空间: disclaimer banner + annualised total + composition bar. */
defineProps<{ notice: string; label: string; total: string; unit: string; parts: A7OptPart[] }>()

const PART_C = ['#5B8FD9', '#9DBCE9', BRAND]
const PART_INK = ['#fff', '#0F1A2E', '#fff']
</script>

<template>
  <div class="rounded-[10px] bg-warn-soft px-3.5 py-2.5 text-xs font-medium text-[#7A4510]">{{ notice }}</div>
  <div class="flex items-end gap-7">
    <div>
      <div class="text-xs text-ink-4">{{ label }}</div>
      <div class="yb-num text-[44px] leading-[1.1] font-semibold">
        {{ total }}<span class="font-sans text-base text-ink-4"> {{ unit }}</span>
      </div>
    </div>
    <div class="flex h-7 flex-1 overflow-hidden rounded-md text-[11px]">
      <span
        v-for="(p, i) in parts"
        :key="p.name"
        class="flex items-center pl-2"
        :style="{ width: p.pct + '%', background: PART_C[i % 3], color: PART_INK[i % 3] }"
      >{{ p.name }} {{ p.value }}</span>
    </div>
  </div>
</template>
