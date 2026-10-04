<script setup lang="ts">
import { useElementSize } from '@vueuse/core'
import { computed, ref } from 'vue'
import type { Offsite } from '@/api/holo'

/**
 * 区域外流向图：左侧“示例市 · 参保地”→ 贝塞尔曲线 → 右侧就医地区；线宽 ∝ 占比（引擎给出），省内蓝、省外橙、其他灰。
 * 只到地区汇总，不出现就医地机构。
 */
const props = defineProps<{ flows: Offsite['flows']; origin?: string }>()
const box = ref<HTMLElement | null>(null)
const { width } = useElementSize(box)
const H = 380
const W = computed(() => Math.max(width.value, 420))
/** 终点竖条 x：约 71% 宽度（右侧留给地区名与金额）。 */
const endX = computed(() => Math.round(W.value * 0.7133))
const color = (s: string) => (s === '省内' ? 'var(--c-primary-solid)' : s === '省外' ? 'var(--c-chart-amber-dk)' : 'var(--c-text-faint)')
const rows = computed(() =>
  props.flows.map((f, i) => {
    const y = 38 + i * 58
    const sx = 122
    const ex = endX.value
    const c1 = sx + (ex - sx) * 0.48
    const c2 = sx + (ex - sx) * 0.55
    return { ...f, y, c: color(f.scope), d: `M${sx} 190 C ${c1} 190, ${c2} ${y}, ${ex} ${y}` }
  }),
)
</script>

<template>
  <div ref="box" class="relative w-full" :style="{ height: `${H}px` }" data-testid="flow-map">
    <svg v-if="width" :width="W" :height="H" class="absolute inset-0 block">
      <g v-for="f in rows" :key="f.region">
        <path :d="f.d" fill="none" :stroke="f.c" stroke-opacity="0.38" :stroke-width="f.strokeWidth" stroke-linecap="butt">
          <title>{{ f.region }} · {{ f.scope }} · {{ f.sharePct }}% · {{ f.amountText }}</title>
        </path>
        <rect :x="endX" :y="f.y - 18" width="6" height="36" rx="2" :fill="f.c" />
      </g>
    </svg>
    <div class="absolute top-[150px] left-0 flex h-20 w-[108px] flex-col items-center justify-center rounded-md bg-primary-solid leading-[1.3] text-white">
      <span class="text-[14px] font-semibold">{{ origin ?? '示例市' }}</span>
      <span class="text-[11px] opacity-80">参保地</span>
    </div>
    <div v-for="f in rows" :key="f.region" class="absolute leading-[1.35] whitespace-nowrap" :style="{ left: `${endX + 14}px`, top: `${f.y - 18}px` }" :data-flow="f.region">
      <div class="text-[13px] font-semibold text-ink">{{ f.region }}</div>
      <div class="text-[11px] text-ink-muted tabular-nums">{{ f.sharePct.toFixed(1) }}% · {{ f.amountText }}</div>
    </div>
  </div>
</template>
