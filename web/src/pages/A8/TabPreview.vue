<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import type { A8PreviewRow } from '@/mock/A8'
import { useA8 } from './store'

const s = useA8()

const TONE: Record<A8PreviewRow['tone'], string> = {
  bad: 'text-bad',
  ok: 'text-ok-ink',
  warn: 'text-warn-ink',
  ink: 'text-ink-1',
  muted: 'text-ink-5',
}
const aud = computed(() => s.d.audiences[s.pvi] ?? s.d.audiences[0]!)
const side = computed(() => {
  const rows = aud.value.rows
  return [
    { k: '身份', v: aud.value.identity },
    { k: '可见指标', v: rows.filter(r => !r.hidden).length + ' / ' + rows.length },
    { k: '不渲染', v: rows.filter(r => r.hidden).map(r => r.label).join('、') },
  ]
})
</script>

<template>
  <div class="mb-3.5 flex flex-wrap items-center gap-2.5">
    <span class="text-xs whitespace-nowrap text-ink-4">以此身份查看</span>
    <div class="flex rounded-lg bg-line-2 p-[3px]">
      <button type="button"
        v-for="(p, i) in s.d.audiences"
        :key="p.name"
        :class="cn(
          'cursor-pointer rounded-md px-3 py-[5px] text-xs whitespace-nowrap max-xl:min-h-10',
          i === s.pvi ? 'bg-white text-ink-1 shadow-[0_1px_2px_rgba(15,23,42,.1)]' : 'text-ink-4',
        )"
        @click="s.pvi = i"
      >{{ p.name }}</button>
    </div>
    <span class="text-xs text-ink-5">斜纹区域为该身份不渲染的内容</span>
  </div>
  <div class="grid grid-cols-[minmax(0,1fr)_260px] gap-4 max-lg:grid-cols-1">
    <div class="flex flex-col gap-3 rounded-[10px] border border-line-1 px-[26px] py-[22px] shadow-[0_2px_8px_rgba(15,23,42,.05)]">
      <div class="text-[11px] text-ink-5">{{ s.d.previewHead }} · {{ aud.name }}版</div>
      <div class="text-lg font-semibold">{{ s.cur.name }}</div>
      <div
        v-for="r in aud.rows"
        :key="r.label"
        :class="cn(
          'grid grid-cols-[150px_minmax(0,1fr)_110px] items-center gap-3 rounded-lg px-2.5 py-2 text-[13px]',
          r.hidden ? 'bg-[repeating-linear-gradient(135deg,#F4F6F9_0_6px,#FFFFFF_6px_12px)]' : 'bg-surface-1',
        )"
      >
        <span class="whitespace-nowrap text-ink-3">{{ r.label }}</span>
        <span :class="cn('overflow-hidden text-xs text-ellipsis whitespace-nowrap', r.hidden ? 'text-ink-5' : 'text-ink-4')">{{ r.method }}</span>
        <span :class="cn('yb-num text-right font-semibold whitespace-nowrap', TONE[r.tone])">{{ r.value }}</span>
      </div>
    </div>
    <div class="flex flex-col gap-2.5">
      <div v-for="x in side" :key="x.k" class="rounded-[10px] bg-surface-1 px-3.5 py-3 text-xs">
        <div class="text-ink-4">{{ x.k }}</div>
        <div class="mt-0.5 font-semibold">{{ x.v }}</div>
      </div>
    </div>
  </div>
</template>
