<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { A, BRAND } from '@/lib/palette'
import type { A3Row } from './types'

export type A3PullState = 'idle' | 'load' | 'err'

const props = defineProps<{ row: A3Row; pull: A3PullState }>()
const emit = defineEmits<{ pull: []; notify: [] }>()

const late = computed(() => props.row.status === 'late')

const quality = computed(() => {
  const s = props.row.src
  const q: [string, number][] = [
    ['完整性', late.value ? 0 : 98.2],
    ['一致性', late.value ? 0 : 96.7],
    ['及时性', late.value ? 0 : s.history.includes('late') ? 91.7 : 100],
  ]
  return q.map(([k, v]) => ({ k, v: v ? v + '%' : '—' }))
})

const lineage = computed(() => {
  const s = props.row.src
  const L = s.lineage
  if (!L) return []
  const items: [string, string][] = [
    ['指标卡', L.indicator + ' ' + L.version],
    ['取数批次', late.value ? '本期未生成' : L.batch],
    ['主题库表', L.martTable],
    ['源表', L.sourceTable],
    ['数据源', s.name],
  ]
  return items.map(([k, v], i) => ({
    k,
    v,
    c: i === 1 && late.value ? A : i === 0 ? BRAND : '#98A2B3',
    line: i < items.length - 1,
  }))
})
</script>

<template>
  <div class="yb-card flex flex-col gap-3.5 px-card-x py-card-y">
    <div>
      <div class="text-base font-semibold">{{ row.src.name }}</div>
      <div class="text-xs text-ink-4">{{ row.src.provider }} · {{ row.src.mode }} · 应到 {{ row.src.due }}</div>
    </div>

    <template v-if="late">
      <div class="rounded-[10px] bg-bad-soft px-3.5 py-3 text-xs leading-[1.6] text-bad-ink">
        <b>未按时到达 · 已逾期 {{ row.src.overdueDays ?? 0 }} 天</b><br>依赖指标本期暂缓计算,已自动通知省平台对接人。
      </div>
      <Button v-if="pull === 'idle'" variant="outline" class="h-[34px] gap-1.5 font-normal" @click="emit('pull')">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#445066" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5" /></svg>重新拉取
      </Button>
      <div v-if="pull === 'load'" class="flex h-[34px] items-center justify-center gap-2 rounded-lg bg-surface-2 text-xs text-ink-3">
        <span class="size-3.5 animate-yb-spin rounded-full border-2 border-line-4 border-t-brand" />正在连接省平台交换接口…
      </div>
      <div v-if="pull === 'err'" class="flex flex-col gap-2 rounded-[10px] border border-bad-line bg-white p-3">
        <div class="flex items-center gap-2 text-xs font-semibold text-bad-ink">
          <span class="flex size-[18px] items-center justify-center rounded-full bg-bad text-[11px] text-white">!</span>省平台接口超时 · HTTP 504
        </div>
        <div class="font-mono text-[11px] text-ink-4">trace 7f3a-c2e1 · 10:12:44 · 等待 30s 无响应</div>
        <div class="flex gap-2">
          <Button class="h-[30px] flex-1 text-xs" @click="emit('pull')">重试</Button>
          <Button variant="outline" class="h-[30px] flex-1 text-xs font-normal" @click="emit('notify')">通知对接人</Button>
        </div>
      </div>
    </template>

    <div class="grid grid-cols-3 gap-2">
      <div v-for="q in quality" :key="q.k" class="rounded-[10px] bg-surface-2 px-3 py-2.5">
        <div class="text-[11px] text-ink-4">{{ q.k }}</div>
        <div class="yb-num text-xl font-semibold">{{ q.v }}</div>
      </div>
    </div>

    <div>
      <div class="mb-1.5 text-xs text-ink-4">依赖指标</div>
      <div class="flex flex-col gap-1">
        <div v-for="d in row.src.dependents" :key="d" class="flex items-center justify-between rounded-lg bg-surface-1 px-2.5 py-[7px]">
          <span>{{ d }}</span>
          <Badge :variant="late ? 'warn' : 'ok'" class="py-px font-normal">{{ late ? '本期暂缓' : '可计算' }}</Badge>
        </div>
      </div>
    </div>

    <div v-if="lineage.length">
      <div class="mb-2 text-xs text-ink-4">血缘</div>
      <div v-for="l in lineage" :key="l.k" class="flex gap-2.5">
        <div class="flex w-2.5 flex-col items-center">
          <span class="mt-[5px] size-2 rounded-full" :style="{ background: l.c }" />
          <span v-if="l.line" class="w-px flex-1 bg-line-1" />
        </div>
        <div class="pb-2.5">
          <div class="text-[11px] text-ink-5">{{ l.k }}</div>
          <div class="font-mono text-xs">{{ l.v }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
