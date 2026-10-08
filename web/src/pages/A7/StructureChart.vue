<script setup lang="ts">
import type { A7StructureRow } from '@/mock/A7'
import { RAMP, RAMP_INK } from './colors'

/** §02 费用结构: 100% stacked bars (全市 / 标杆组 / 偏离组) by cost category. */
defineProps<{ categories: string[]; rows: A7StructureRow[] }>()
</script>

<template>
  <div class="flex flex-col gap-2">
    <div v-for="r in rows" :key="r.name" class="grid grid-cols-[56px_1fr] items-center gap-2.5">
      <span class="text-xs text-ink-3">{{ r.name }}</span>
      <div class="flex h-6 overflow-hidden rounded-md">
        <span
          v-for="(v, j) in r.shares"
          :key="j"
          class="flex items-center justify-center text-[11px]"
          :style="{ width: v + '%', background: RAMP[j % RAMP.length], color: RAMP_INK[j % RAMP_INK.length] }"
        >{{ v }}</span>
      </div>
    </div>
  </div>
  <div class="flex flex-wrap gap-x-3.5 gap-y-1 pl-[66px] text-[11px] text-ink-4">
    <span v-for="(c, j) in categories" :key="c" class="flex items-center gap-1">
      <span class="size-[9px] rounded-[2px]" :style="{ background: RAMP[j % RAMP.length] }" />{{ c }}
    </span>
  </div>
</template>
