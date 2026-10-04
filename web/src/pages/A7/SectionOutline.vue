<script setup lang="ts">
import { cn } from '@/lib/utils'
import { pad } from '@/lib/format'

export interface OutlineItem { name: string; ok: boolean; comments: number }
defineProps<{ items: OutlineItem[]; active: number }>()
defineEmits<{ go: [index: number] }>()
</script>

<template>
  <aside class="sticky top-[calc(var(--sticky-top)+136px)] flex flex-col gap-1 py-5 pr-3 pl-6">
    <div class="px-2.5 pb-1.5 text-[11px] font-semibold tracking-[.5px] text-ink-5">七段式结构</div>
    <div
      v-for="(o, i) in items"
      :key="o.name"
      role="button"
      tabindex="0"
      :class="cn(
        'flex cursor-pointer items-center gap-2.5 rounded-[10px] border px-2.5 py-[9px] hover:bg-white',
        i === active ? 'border-line-1 bg-white' : 'border-transparent bg-transparent',
      )"
      @click="$emit('go', i)"
      @keydown.enter="$emit('go', i)"
    >
      <span class="yb-num w-[18px] text-xs text-ink-5">{{ pad(i + 1) }}</span>
      <div class="min-w-0 flex-1">
        <div :class="cn('text-[13px]', i === active ? 'font-semibold' : 'font-medium')">{{ o.name }}</div>
        <div :class="cn('flex items-center gap-1 text-[11px]', o.ok ? 'text-ok-ink' : 'text-warn-ink')">
          <span :class="cn('size-1.5 rounded-full', o.ok ? 'bg-ok' : 'bg-warn')" />{{ o.ok ? '已审定' : '待审定' }}
        </div>
      </div>
      <span
        v-if="o.comments > 0"
        class="flex h-[18px] min-w-[18px] items-center justify-center rounded-[9px] bg-violet-soft px-[5px] text-[11px] font-semibold text-violet"
      >{{ o.comments }}</span>
    </div>
  </aside>
</template>
