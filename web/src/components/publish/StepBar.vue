<script setup lang="ts">
import type { FlowStep } from '@/api/publish'

/**
 * 十步进度条（步名取自 A9 流程模板节点）：完成绿 ✓、当前蓝、未到灰；
 * 「机构同步核对」与专家组审核并行（副标）；必经节点下方红色徽标「未批准不外发」。
 */
const props = defineProps<{ steps: FlowStep[]; step: number; archived: boolean }>()

const state = (n: number) => (props.archived || n < props.step ? 'done' : n === props.step ? 'on' : 'todo')
</script>

<template>
  <ol class="grid" :style="{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }" data-testid="step-bar">
    <li v-for="(s, i) in steps" :key="s.idx" class="relative flex flex-col items-center" :data-step="s.idx" :data-state="state(s.idx)">
      <span
        v-if="i < steps.length - 1"
        class="absolute top-[13px] right-[-50%] left-1/2 h-0.5"
        :class="state(s.idx) === 'done' ? 'bg-success-solid' : 'bg-line'"
        aria-hidden="true"
      />
      <span
        class="relative flex size-7 items-center justify-center rounded-full border-[1.5px] text-[12px] font-semibold"
        :class="{
          'border-success-solid bg-success-solid text-white': state(s.idx) === 'done',
          'border-primary bg-primary-solid text-white ring-4 ring-primary-tint': state(s.idx) === 'on',
          'border-line-strong bg-surface text-ink-faint': state(s.idx) === 'todo',
        }"
      >{{ state(s.idx) === 'done' ? '✓' : s.idx }}</span>
      <span
        class="mt-1.5 text-center text-[12px]"
        :class="state(s.idx) === 'on' ? 'font-semibold text-primary' : state(s.idx) === 'done' ? 'text-ink' : 'text-ink-faint'"
      >{{ s.name }}</span>
      <span class="text-[10px] leading-[1.5] text-ink-muted">{{ s.sub }}</span>
      <span
        v-if="s.gate"
        class="mt-0.5 rounded-[4px] border border-danger-line bg-danger-soft px-1.5 text-[10px] leading-[1.7] font-semibold whitespace-nowrap text-danger-ink"
        data-testid="gate-badge"
      >未批准不外发</span>
    </li>
  </ol>
</template>
