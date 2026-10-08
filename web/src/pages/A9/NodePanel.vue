<script setup lang="ts">
import { pad } from '@/lib/format'
import { cn } from '@/lib/utils'
import { KIND, chipCls, type FlowNode } from './model'

defineProps<{
  node: FlowNode
  note: string
  lanes: string[]
  timeoutActions: string[]
  timeout: number
  channels: string[]
  channelOn: Record<string, boolean>
  stages: string[]
  canRemove: boolean
}>()
const emit = defineEmits<{
  lane: [l: string]
  days: [d: number]
  timeout: [i: number]
  channel: [c: string]
  name: [n: string]
  col: [c: number]
  remove: []
}>()
</script>

<template>
  <div class="overflow-hidden rounded-[var(--radius-card)] border border-line-1 bg-white">
    <div
      class="flex items-center gap-3 border-b border-line-2 px-[18px] py-4"
      :style="{ background: `linear-gradient(135deg, ${KIND[node.kind].cb} 0%, #fff 70%)` }"
    >
      <span class="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-white" :style="{ boxShadow: `0 0 0 1px ${KIND[node.kind].cb}` }">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" :style="{ stroke: KIND[node.kind].c }" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="KIND[node.kind].icon" /></svg>
      </span>
      <div class="min-w-0">
        <div class="text-[11px] whitespace-nowrap text-ink-4">节点 {{ pad(node.idx + 1) }} · {{ node.kind }}</div>
        <div class="text-[17px] font-semibold whitespace-nowrap">{{ node.name }}</div>
      </div>
    </div>
    <div class="flex flex-col gap-4 px-[18px] py-4">
      <div class="grid grid-cols-2 gap-2">
        <label class="flex flex-col gap-1 text-xs text-ink-4">节点名称
          <input
            :value="node.name"
            maxlength="16"
            aria-label="节点名称"
            class="h-8 rounded-lg border border-line-1 bg-surface-1 px-2.5 text-[13px] text-ink-1 outline-none focus:border-brand-line max-xl:h-11"
            @change="emit('name', ($event.target as HTMLInputElement).value.trim() || node.name)"
          >
        </label>
        <label class="flex flex-col gap-1 text-xs text-ink-4">所在阶段
          <select
            :value="node.col"
            aria-label="所在阶段"
            class="h-8 rounded-lg border border-line-1 bg-surface-1 px-2 text-[13px] text-ink-1 outline-none focus:border-brand-line max-xl:h-11"
            @change="emit('col', Number(($event.target as HTMLSelectElement).value))"
          >
            <option v-for="(st, i) in stages" :key="st" :value="i">{{ i + 1 }} · {{ st }}</option>
          </select>
        </label>
      </div>
      <div>
        <div class="mb-2 text-xs text-ink-4">承办角色</div>
        <div class="flex flex-wrap gap-1.5">
          <button type="button"
            v-for="l in lanes"
            :key="l"
            :class="cn('cursor-pointer rounded-lg border px-2.5 py-1 text-xs whitespace-nowrap max-xl:min-h-10', chipCls(l === node.lane))"
            @click="emit('lane', l)"
          >{{ l }}</button>
        </div>
      </div>
      <div class="flex items-center justify-between">
        <div>
          <div class="text-xs text-ink-4">办理时限</div>
          <div class="text-[11px] text-ink-5">工作日 · 0 为即时</div>
        </div>
        <div class="flex items-center overflow-hidden rounded-lg border border-line-1">
          <button type="button" class="flex size-8 max-xl:size-11 cursor-pointer items-center justify-center bg-surface-1 select-none" aria-label="减少时限天数" @click="emit('days', Math.max(0, node.days - 1))">−</button>
          <span class="yb-num min-w-11 text-center text-[17px] font-semibold">{{ node.days }}</span>
          <button type="button" class="flex size-8 max-xl:size-11 cursor-pointer items-center justify-center bg-surface-1 select-none" aria-label="增加时限天数" @click="emit('days', node.days + 1)">+</button>
        </div>
      </div>
      <div>
        <div class="mb-2 text-xs text-ink-4">超时动作</div>
        <div class="grid grid-cols-2 gap-1.5">
          <button type="button"
            v-for="(l, i) in timeoutActions"
            :key="l"
            :class="cn('cursor-pointer rounded-lg border px-2.5 py-[7px] text-center text-xs whitespace-nowrap max-xl:min-h-10', chipCls(i === timeout))"
            @click="emit('timeout', i)"
          >{{ l }}</button>
        </div>
      </div>
      <div>
        <div class="mb-2 text-xs text-ink-4">通知渠道</div>
        <div class="flex flex-wrap gap-1.5">
          <button type="button"
            v-for="c in channels"
            :key="c"
            :class="cn('cursor-pointer rounded-full border px-2.5 py-1 text-xs whitespace-nowrap max-xl:min-h-10', chipCls(!!channelOn[c]))"
            @click="emit('channel', c)"
          >{{ c }}</button>
        </div>
      </div>
      <div class="rounded-[10px] bg-surface-1 px-3 py-2.5 text-xs leading-[1.7] text-ink-3">{{ note }}</div>
      <button
        type="button"
        :disabled="!canRemove"
        class="h-8 cursor-pointer rounded-lg border border-[#F3C5C0] text-xs text-bad disabled:cursor-not-allowed disabled:opacity-50 max-xl:h-11"
        @click="emit('remove')"
      >删除此节点</button>
    </div>
  </div>
</template>
