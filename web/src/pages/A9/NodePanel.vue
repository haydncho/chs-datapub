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
}>()
const emit = defineEmits<{
  lane: [l: string]
  days: [d: number]
  timeout: [i: number]
  channel: [c: string]
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
      <div>
        <div class="mb-2 text-xs text-ink-4">承办角色</div>
        <div class="flex flex-wrap gap-1.5">
          <button type="button"
            v-for="l in lanes"
            :key="l"
            :class="cn('cursor-pointer rounded-lg border px-2.5 py-1 text-xs whitespace-nowrap', chipCls(l === node.lane))"
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
          <button type="button" class="flex size-8 cursor-pointer items-center justify-center bg-surface-1 select-none" aria-label="减少时限天数" @click="emit('days', Math.max(0, node.days - 1))">−</button>
          <span class="yb-num min-w-11 text-center text-[17px] font-semibold">{{ node.days }}</span>
          <button type="button" class="flex size-8 cursor-pointer items-center justify-center bg-surface-1 select-none" aria-label="增加时限天数" @click="emit('days', node.days + 1)">+</button>
        </div>
      </div>
      <div>
        <div class="mb-2 text-xs text-ink-4">超时动作</div>
        <div class="grid grid-cols-2 gap-1.5">
          <button type="button"
            v-for="(l, i) in timeoutActions"
            :key="l"
            :class="cn('cursor-pointer rounded-lg border px-2.5 py-[7px] text-center text-xs whitespace-nowrap', chipCls(i === timeout))"
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
            :class="cn('cursor-pointer rounded-full border px-2.5 py-1 text-xs whitespace-nowrap', chipCls(!!channelOn[c]))"
            @click="emit('channel', c)"
          >{{ c }}</button>
        </div>
      </div>
      <div class="rounded-[10px] bg-surface-1 px-3 py-2.5 text-xs leading-[1.7] text-ink-3">{{ note }}</div>
    </div>
  </div>
</template>
