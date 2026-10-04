<script setup lang="ts">
import type { TodoGroup, TodoItem } from '@/api/publish'
import { toneText } from '@/lib/tone'

/** 左侧「我的待办」：六类发布流程 + 对标档位切换 + 指标上线审批；每项显示当前步与时限，第 5 步红字。 */
defineProps<{ groups: TodoGroup[]; selected: { type: string; id: number } | null }>()
defineEmits<{ select: [item: TodoItem] }>()
</script>

<template>
  <nav class="flex flex-col" data-testid="todo-list">
    <div v-for="g in groups" :key="g.group" class="mb-1.5" :data-group="g.group">
      <div class="flex items-center justify-between px-1.5 pt-1.5 pb-1 text-[11px] text-ink-faint">
        <span>{{ g.group }}</span>
        <span v-if="g.items.length" class="tabular-nums">{{ g.items.length }}</span>
      </div>
      <div v-if="!g.items.length" class="px-2.5 py-1.5 text-[11px] text-ink-ghost">暂无待办</div>
      <button
        v-for="it in g.items"
        :key="it.type + it.id"
        type="button"
        class="mb-0.5 block w-full cursor-pointer rounded-lg border px-2.5 py-2 text-left transition-colors"
        :class="selected?.type === it.type && selected.id === it.id ? 'border-primary bg-primary-tint' : 'border-transparent hover:bg-hover'"
        :data-todo="it.name"
        @click="$emit('select', it)"
      >
        <div class="flex items-center gap-1.5 text-[13px] font-medium text-ink">
          <span class="min-w-0 truncate" :title="it.name">{{ it.name }}</span>
          <span v-if="it.source" class="ml-auto flex-none rounded-[3px] border border-line px-1 text-[10px] font-normal text-ink-muted">{{ it.source }}</span>
        </div>
        <div class="mt-0.5 flex justify-between gap-2 text-[11px]">
          <span :class="toneText[it.statusTone]" data-testid="todo-status">{{ it.status }}</span>
          <span :class="toneText[it.dueTone]">{{ it.due }}</span>
        </div>
      </button>
    </div>
  </nav>
</template>
