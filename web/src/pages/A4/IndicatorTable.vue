<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { cn } from '@/lib/utils'
import { COLUMNS, GROUP_COLOR, SOURCE_TAG, STATUS, TIER_LABEL, type A4Row } from './meta'
import { vPress } from '@/lib/a11y'

defineProps<{
  rows: A4Row[]
  gridTemplate: string
  pad: string
  hidden: Record<number, boolean>
  sort: { k: number; d: 1 | -1 }
}>()
const emit = defineEmits<{
  select: [i: number]
  check: [row: A4Row]
  sort: [col: number]
  resize: [col: number, e: MouseEvent]
  clear: []
}>()
</script>

<template>
  <div class="yb-card overflow-clip">
   <div class="max-xl:overflow-x-auto">
    <div class="max-xl:w-max max-xl:min-w-full">
    <div
      class="xl:sticky top-(--sticky-top) z-[6] grid gap-2.5 border-b border-line-2 bg-surface-1 px-4 py-2.5 text-xs text-ink-4"
      :style="{ gridTemplateColumns: gridTemplate }"
    >
      <span />
      <template v-for="(c, ci) in COLUMNS" :key="c.key">
        <button type="button"
          v-if="!(ci > 0 && hidden[ci])"
          :class="cn(
            'relative flex cursor-pointer items-center gap-[3px] whitespace-nowrap hover:text-ink-1 max-xl:min-h-10',
            sort.k === ci ? 'font-semibold text-ink-1' : 'font-normal text-ink-4',
          )"
          @click="emit('sort', ci)"
        >
          {{ c.label }}<span class="text-[9px]" :class="sort.k === ci ? 'opacity-100' : 'opacity-35'">{{ sort.k === ci ? (sort.d > 0 ? '▲' : '▼') : '↕' }}</span>
          <span
            v-if="ci > 0"
            title="拖动调整列宽"
            class="absolute -top-2.5 -right-[7px] -bottom-2.5 block w-[9px] cursor-col-resize border-r-2 border-transparent hover:border-brand-line"
            @mousedown="emit('resize', ci, $event)"
            @click.stop
          />
        </button>
      </template>
    </div>

    <div v-if="rows.length === 0" class="flex flex-col items-center gap-2 px-5 py-12 text-ink-4">
      <span class="flex size-11 items-center justify-center rounded-xl bg-surface-3">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="#98A2B3" stroke-width="1.8" stroke-linecap="round"><path d="M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.3-4.3" /></svg>
      </span>
      <div class="font-semibold text-ink-2">当前筛选下没有指标</div>
      <div class="text-xs">试试切换维度或将来源设为“全部”</div>
      <button type="button" class="mt-1 cursor-pointer text-xs text-brand" @click="emit('clear')">清除筛选</button>
    </div>

    <div v-press
      v-for="r in rows"
      :key="r.i"
      :class="cn(
        'grid cursor-pointer items-center gap-2.5 yb-tr border-b border-line-3 px-4 hover:bg-surface-1 max-xl:min-h-11',
        r.selected ? 'bg-brand-tint shadow-[inset_3px_0_0_var(--brand)]' : 'bg-white',
      )"
      :style="{ gridTemplateColumns: gridTemplate, paddingTop: pad, paddingBottom: pad }"
      @click="emit('select', r.i)"
    >
      <Checkbox
        :model-value="r.checked"
        :class="cn(
          'size-4 rounded-[4px] border-[1.5px] bg-white shadow-none max-xl:relative max-xl:size-5 max-xl:after:absolute max-xl:after:-inset-2.5 data-[state=checked]:border-brand data-[state=checked]:bg-brand',
          r.intl ? 'cursor-not-allowed border-line-1' : 'cursor-pointer border-ink-6',
        )"
        :aria-label="'选择 ' + r.ind.name"
        @click.stop="emit('check', r)"
      >
        <span class="text-[11px] leading-none text-white">✓</span>
      </Checkbox>
      <div class="flex min-w-0 items-center gap-2" :class="r.intl && 'opacity-50'">
        <span class="min-w-0 truncate font-medium" :title="r.ind.name">{{ r.ind.name }}</span>
        <span
          :class="cn(
            'shrink-0 rounded-[4px] px-1.5 py-px text-[10px] whitespace-nowrap',
            r.ind.source === 'national' ? 'bg-brand-soft text-brand' : 'bg-line-3 text-ink-4',
          )"
        >{{ SOURCE_TAG[r.ind.source] }}</span>
      </div>
      <span class="flex items-center gap-[5px]" :class="r.intl && 'opacity-50'">
        <span class="size-[7px] rounded-full" :style="{ background: GROUP_COLOR[r.ind.group] }" />{{ r.ind.group }}
      </span>
      <span
        v-if="!hidden[2]"
        :class="cn('block truncate text-xs whitespace-nowrap', r.pending ? 'text-warn-ink' : 'text-ink-2', r.intl && 'opacity-50')"
      >{{ TIER_LABEL[r.ind.tier] }}{{ r.pending ? ' → 审批中' : '' }}</span>
      <span v-if="!hidden[3]" class="block text-xs" :class="r.intl && 'opacity-50'">{{ r.ind.freq }}</span>
      <span v-if="!hidden[4]" class="block font-mono text-[11px] text-ink-3" :class="r.intl && 'opacity-50'">{{ r.ind.version }}</span>
      <span v-if="!hidden[5]" class="block text-xs text-ink-3" :class="r.intl && 'opacity-50'">{{ r.ind.refs ? r.ind.refs + ' 份' : '—' }}</span>
      <Badge :variant="STATUS[r.ind.status].variant" class="w-full">{{ STATUS[r.ind.status].label }}</Badge>
    </div>
    </div>
   </div>
  </div>
</template>
