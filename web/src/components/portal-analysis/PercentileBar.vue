<script setup lang="ts">
/**
 * 分位条（交接说明 · 通用控件）：浅灰底；P25–P75 带浅蓝；P50 竖线 1.5px；
 * 本院标记 4px 宽、比条高 8px、圆角 2px，≥P70 且「高为差」时橙色（concern），否则蓝色；下方刻度 P0/P25/P50/P75/P100。
 * 刻度等距，标记位置 = 本院分位（引擎计算）。
 */
const props = withDefaults(defineProps<{ pct: number; concern?: boolean; height?: number }>(), { concern: false, height: 16 })
const left = () => `${Math.min(100, Math.max(0, props.pct))}%`
</script>

<template>
  <div data-testid="pct-bar" :data-pct="pct" :data-concern="concern">
    <div class="relative rounded-[3px] bg-[var(--pa-track)]" :style="{ height: `${height}px` }">
      <div class="absolute inset-y-0 left-1/4 w-1/2 bg-[var(--pa-band)]" />
      <div class="absolute -inset-y-0.5 left-1/2 border-l-[1.5px] border-[var(--c-text-muted)]" />
      <div
        class="absolute -top-1 -ml-0.5 w-1 rounded-[2px]"
        :style="{ left: left(), height: `${height + 8}px`, background: concern ? 'var(--c-chart-amber-dk)' : 'var(--c-primary-solid)' }"
        data-testid="pct-marker"
      />
    </div>
    <div class="mt-1 flex justify-between text-[10px] text-ink-faint">
      <span>P0</span><span>P25</span><span>P50</span><span>P75</span><span>P100</span>
    </div>
  </div>
</template>

<style scoped>
div {
  --pa-track: var(--c-chip);
  --pa-band: var(--c-primary-soft);
}
</style>
