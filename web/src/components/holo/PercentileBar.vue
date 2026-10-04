<script setup lang="ts">
/**
 * 同级分位条（交接说明“通用控件 · 分位条”）：底条 + P25–P75 浅蓝带 + P50 竖线 + 本院 / 本统筹区标记（比条高 8px、4px 宽）。
 * 只呈现匿名分位，不出现任何机构名称。
 */
const props = withDefaults(defineProps<{ pct: number; tone?: 'danger' | 'warning' | 'primary'; height?: number; ticks?: boolean }>(), {
  tone: 'primary',
  height: 10,
  ticks: false,
})
const color = { danger: 'var(--c-red-solid)', warning: 'var(--c-chart-amber-dk)', primary: 'var(--c-primary-solid)' }
</script>

<template>
  <div>
    <div class="relative rounded-[3px] bg-divider" :style="{ height: `${props.height}px` }" role="meter" :aria-valuenow="pct" aria-valuemin="0" aria-valuemax="100">
      <div class="absolute inset-y-0 left-1/4 w-1/2 bg-primary-soft" />
      <div class="absolute -top-0.5 -bottom-0.5 left-1/2 border-l-[1.5px] border-ink-muted" />
      <div
        class="absolute w-1 rounded-[2px]"
        :style="{ left: `calc(${pct}% - 2px)`, top: '-4px', height: `${props.height + 8}px`, background: color[tone] }"
        data-marker
      />
    </div>
    <div v-if="ticks" class="mt-1 flex justify-between text-[10px] text-ink-faint tabular-nums"><span>P0</span><span>P25</span><span>P50</span><span>P75</span><span>P100</span></div>
  </div>
</template>
