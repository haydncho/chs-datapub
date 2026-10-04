<script setup lang="ts">
/**
 * 分位条（交接说明·通用控件）：底条 + P25–P75 带 + P50 竖线 + 本院标记（≥P70 且“高为差”时橙色，否则蓝色）+ 刻度。
 */
const props = withDefaults(defineProps<{ pct?: number | null; highIsBad?: boolean; height?: number; ticks?: boolean }>(), {
  pct: null,
  highIsBad: false,
  height: 14,
  ticks: true,
})
const warn = () => props.highIsBad && props.pct != null && props.pct >= 70
</script>

<template>
  <div>
    <div class="relative rounded-[3px] bg-divider" :style="{ height: `${height}px` }">
      <div class="absolute inset-y-0 left-1/4 w-1/2 bg-primary-soft" />
      <div class="absolute -inset-y-0.5 left-1/2 border-l-[1.5px] border-ink-muted" />
      <div
        v-if="pct != null"
        data-testid="pct-marker"
        class="absolute -ml-0.5 w-1 rounded-[2px]"
        :style="{ left: `${pct}%`, top: '-4px', height: `${height + 8}px`, background: warn() ? 'var(--c-warning-solid)' : 'var(--c-primary-solid)' }"
      />
    </div>
    <div v-if="ticks" class="mt-1 flex justify-between text-[10px] text-ink-faint"><span>P0</span><span>P25</span><span>P50</span><span>P75</span><span>P100</span></div>
  </div>
</template>
