<script setup lang="ts">
import { Check, Eye, Minus } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import type { A12Cell, A12Data, A12Group, A12Level, A12MatrixRow } from '@/mock/A12'
import { SIDE_NAME } from './useUsers'

const props = defineProps<{ data: A12Data }>()

const LEVEL_NAME: Record<A12Level, string> = { access: '可访问', readonly: '只读', none: '无' }
const LEVEL_CHIP: Record<A12Level, string> = {
  access: 'bg-ok-soft text-ok-ink',
  readonly: 'bg-warn-soft text-warn-ink',
  none: 'bg-surface-2 text-ink-6',
}

const GRID = 'grid grid-cols-[minmax(190px,1.3fr)_repeat(8,minmax(0,1fr))] items-center gap-1.5 px-card-x'

function groupOf(id: string): A12Group {
  return props.data.groups.find(g => g.id === id) ?? { id, name: id, pages: [] }
}
function roleOf(code: string) {
  return props.data.roles.find(r => r.code === code)
}
function cellOf(row: A12MatrixRow, gid: string): A12Cell {
  return row.cells.find(c => c.group === gid) ?? { group: gid, level: 'none', pages: [], total: 0 }
}
function has(c: A12Cell, code: string) {
  return c.pages.some(p => p.code === code)
}
</script>

<template>
  <div class="yb-card">
    <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 border-b border-line-2 px-card-x py-3.5">
      <div class="flex items-baseline gap-2.5">
        <span class="text-[15px] font-semibold">角色 × 页面分组 访问矩阵</span>
        <span class="text-xs text-ink-4">取自系统访问策略 · 悬停格子查看具体页面</span>
      </div>
      <div class="flex items-center gap-3.5 text-xs text-ink-3">
        <span class="flex items-center gap-1.5"><span :class="cn('flex size-5 items-center justify-center rounded-md', LEVEL_CHIP.access)"><Check class="size-3.5" /></span>可访问</span>
        <span class="flex items-center gap-1.5"><span :class="cn('flex size-5 items-center justify-center rounded-md', LEVEL_CHIP.readonly)"><Eye class="size-3.5" /></span>只读</span>
        <span class="flex items-center gap-1.5"><span :class="cn('flex size-5 items-center justify-center rounded-md', LEVEL_CHIP.none)"><Minus class="size-3.5" /></span>无</span>
      </div>
    </div>

    <TooltipProvider :delay-duration="80">
      <div :class="[GRID, 'sticky top-(--sticky-top) z-[6] bg-surface-1 py-2.5 text-xs text-ink-4']">
        <span>角色</span>
        <span v-for="g in data.groups" :key="g.id" class="text-center whitespace-nowrap">{{ g.name }}</span>
      </div>
      <div v-for="row in data.matrix" :key="row.role" :class="[GRID, 'border-b border-line-3 py-2.5 last:border-b-0']">
        <div class="min-w-0">
          <div class="flex items-center gap-1.5">
            <span class="truncate font-semibold">{{ row.name }}</span>
            <Badge :variant="roleOf(row.role)?.side === 'bureau' ? 'brand' : 'ok'" class="px-1.5 py-0 font-normal">{{ SIDE_NAME[roleOf(row.role)?.side ?? 'bureau'] }}</Badge>
          </div>
          <div class="mt-0.5 flex flex-wrap items-center gap-x-2 text-[11px] text-ink-5">
            <span>{{ roleOf(row.role)?.users ?? 0 }} 人</span>
            <span>{{ roleOf(row.role)?.dataScope }}</span>
            <span v-if="roleOf(row.role)?.readOnly" class="text-warn-ink">只读</span>
            <span v-if="roleOf(row.role)?.canApprove" class="text-brand">可审批</span>
          </div>
        </div>
        <div v-for="g in data.groups" :key="g.id" class="flex justify-center">
          <Tooltip>
            <TooltipTrigger as-child>
              <button
                type="button"
                :data-level="cellOf(row, g.id).level"
                :aria-label="`${row.name} · ${g.name} · ${LEVEL_NAME[cellOf(row, g.id).level]}`"
                :class="cn('flex h-8 w-[60px] cursor-default items-center justify-center gap-1 rounded-md px-2 text-xs font-medium outline-none focus-visible:ring-2 focus-visible:ring-brand', LEVEL_CHIP[cellOf(row, g.id).level])"
              >
                <Check v-if="cellOf(row, g.id).level === 'access'" class="size-4" />
                <Eye v-else-if="cellOf(row, g.id).level === 'readonly'" class="size-4" />
                <Minus v-else class="size-4" />
                <span
                  v-if="cellOf(row, g.id).level !== 'none' && cellOf(row, g.id).pages.length < cellOf(row, g.id).total"
                  class="yb-num text-[11px] font-medium"
                >{{ cellOf(row, g.id).pages.length }}/{{ cellOf(row, g.id).total }}</span>
              </button>
            </TooltipTrigger>
            <TooltipContent side="top" class="max-w-[260px] bg-ink-1 py-2 text-left">
              <div class="mb-1 font-semibold">{{ row.name }} · {{ g.name }} · {{ LEVEL_NAME[cellOf(row, g.id).level] }}</div>
              <div v-for="p in groupOf(g.id).pages" :key="p.code" class="flex items-center gap-1.5 leading-[1.7]" :class="has(cellOf(row, g.id), p.code) ? '' : 'opacity-50'">
                <Check v-if="has(cellOf(row, g.id), p.code)" class="size-3 shrink-0" />
                <Minus v-else class="size-3 shrink-0" />
                <span>{{ p.name }}</span>
                <span class="yb-num text-[10px] opacity-60">{{ p.code }}</span>
              </div>
            </TooltipContent>
          </Tooltip>
        </div>
      </div>
    </TooltipProvider>
  </div>
</template>
