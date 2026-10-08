<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { A, G, R } from '@/lib/palette'
import type { A3Arrival } from '@/mock/A3'
import type { A3Row } from './types'
import { vPress } from '@/lib/a11y'

defineProps<{ rows: A3Row[]; selected: number }>()
const emit = defineEmits<{ select: [index: number] }>()

const GRID = 'grid grid-cols-[minmax(0,1.6fr)_70px_36px_80px_120px_96px_88px_56px] gap-2.5 px-[18px]'
const HIST: Record<A3Arrival, string> = { ok: G, late: A, missing: R, part: A }

function statusPill(r: A3Row) {
  if (r.status === 'late') return { label: '未按时到达', variant: 'bad' as const, cls: 'text-bad' }
  if (r.status === 'part') return { label: r.src.partLabel ?? '部分到数', variant: 'warn' as const, cls: '' }
  return { label: '已到数', variant: 'ok' as const, cls: '' }
}
const rowsLabel = (w: number) => (w >= 1 ? w.toFixed(1) + ' 万' : '<1 万')
</script>

<template>
  <div class="yb-card overflow-clip">
   <div class="max-xl:overflow-x-auto">
    <div class="max-xl:min-w-[860px]">
    <div :class="cn(GRID, 'xl:sticky top-(--sticky-top) z-[6] border-b border-line-2 bg-surface-1 py-2.5 text-xs text-ink-4')">
      <span>数据源 · 提供方</span><span>接入</span><span>频次</span><span>应到</span><span>近 12 期到数</span><span>质量分</span><span>状态</span><span class="text-right">数据量</span>
    </div>
    <div v-press
      v-for="(r, i) in rows"
      :key="r.src.name"
      :class="cn(
        GRID,
        'cursor-pointer items-center yb-tr border-b border-line-3 py-row hover:bg-surface-1 max-xl:min-h-11',
        i === selected ? 'bg-brand-tint shadow-[inset_3px_0_0_var(--brand)]' : 'bg-white',
      )"
      @click="emit('select', i)"
    >
      <div class="min-w-0">
        <div class="font-medium">{{ r.src.name }}</div>
        <div class="text-[11px] text-ink-4">{{ r.src.provider }}</div>
      </div>
      <span class="text-xs text-ink-3">{{ r.src.mode }}</span>
      <span class="text-xs">{{ r.src.freq }}</span>
      <span class="text-xs text-ink-3">{{ r.src.due }}</span>
      <div class="flex gap-[3px]">
        <span
          v-for="(hc, j) in r.history"
          :key="j"
          class="h-4 w-[9px] rounded-[2px]"
          :style="{ background: HIST[hc], opacity: j === r.history.length - 1 ? 1 : 0.55 }"
        />
      </div>
      <div class="flex items-center gap-2">
        <div class="h-1.5 flex-1 rounded-[3px] bg-line-2">
          <div
            :class="cn('h-1.5 rounded-[3px]', r.src.score < 80 ? 'bg-warn' : 'bg-brand')"
            :style="{ width: (r.status === 'late' ? 0 : r.src.score) + '%' }"
          />
        </div>
        <span class="yb-num w-6 text-right font-semibold">{{ r.status === 'late' ? '—' : r.src.score }}</span>
      </div>
      <Badge :variant="statusPill(r).variant" :class="cn('w-full', statusPill(r).cls)">{{ statusPill(r).label }}</Badge>
      <span class="yb-num text-right text-ink-3" :title="r.status === 'late' ? '本期未到数' : undefined">{{ r.status === 'late' ? '—' : rowsLabel(r.src.rowsWan) }}</span>
    </div>
    </div>
   </div>
    <div class="flex gap-4 px-[18px] py-2.5 text-[11px] text-ink-4">
      <span class="flex items-center gap-[5px]"><span class="size-[9px] rounded-[2px] bg-ok" />按时</span>
      <span class="flex items-center gap-[5px]"><span class="size-[9px] rounded-[2px] bg-warn" />延迟 / 部分</span>
      <span class="flex items-center gap-[5px]"><span class="size-[9px] rounded-[2px] bg-bad" />未到</span>
      <span>最右一格为本期</span>
    </div>
  </div>
</template>
