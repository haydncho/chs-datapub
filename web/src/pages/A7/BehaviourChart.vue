<script setup lang="ts">
import { computed } from 'vue'
import { A, R } from '@/lib/palette'
import type { A7Behaviour } from '@/mock/A7'

/** §03 关键行为: incidence-rate bar + cost-multiplier dot on a ×1.0–×2.5 scale. */
const props = defineProps<{ headers: string[]; rateMax: number; multMin: number; multMax: number; items: A7Behaviour[] }>()

const rows = computed(() =>
  props.items.map(b => ({
    name: b.name,
    rate: b.rate.toFixed(1) + '%',
    rw: (b.rate / props.rateMax) * 100 + '%',
    mult: '×' + b.multiplier.toFixed(2),
    mx: (((b.multiplier - props.multMin) / (props.multMax - props.multMin)) * 100).toFixed(1) + '%',
    mc: b.multiplier >= 1.5 ? R : A,
  })),
)
</script>

<template>
  <div class="grid grid-cols-[minmax(0,1.4fr)_1fr_1fr] gap-3 pb-1 text-[11px] text-ink-5">
    <span v-for="h in headers" :key="h">{{ h }}</span>
  </div>
  <div v-for="b in rows" :key="b.name" class="grid grid-cols-[minmax(0,1.4fr)_1fr_1fr] items-center gap-3 py-1 text-[13px]">
    <span>{{ b.name }}</span>
    <div class="flex items-center gap-2">
      <div class="h-2 flex-1 rounded bg-line-2">
        <div class="h-2 rounded bg-[#5B8FD9]" :style="{ width: b.rw }" />
      </div>
      <span class="yb-num w-[42px] text-right font-semibold">{{ b.rate }}</span>
    </div>
    <div class="relative h-5">
      <div class="absolute top-[9px] right-[46px] left-0 h-0.5 bg-line-2" />
      <span
        class="absolute top-[3px] -ml-[7px] size-3.5 rounded-full"
        :style="{ left: `calc(${b.mx} * 0.82)`, background: b.mc }"
      />
      <span class="yb-num absolute top-0 right-0 font-semibold" :style="{ color: b.mc }">{{ b.mult }}</span>
    </div>
  </div>
</template>
