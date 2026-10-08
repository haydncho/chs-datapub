<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import { G, A, R, INK, BRAND } from '@/lib/palette'
import type { A8Task } from '@/mock/A8'
import { isUrgent, useA8 } from './store'
import { vPress } from '@/lib/a11y'

const s = useA8()

const view = (t: A8Task) => {
  const k = t.step
  const ur = k < 10 && isUrgent(t)
  return {
    on: t.id === s.flowId,
    due: k >= 10 ? '' : t.due,
    ur,
    dot: k === 5 ? A : k >= 10 ? INK[5] : ur ? R : BRAND,
    lbl: k >= 10 ? '已归档' : k + ' · ' + s.stepName(k),
    lc: k === 5 ? 'var(--warn-ink)' : k >= 10 ? INK[4] : BRAND,
    segs: Array.from({ length: 10 }, (_, i) =>
      i < k - 1 || k >= 10 ? G : i === k - 1 ? (k === 5 ? A : BRAND) : '#E5E9F0',
    ),
  }
}
const groups = computed(() => s.queueGroups.map(g => ({ g: g.g, items: g.items.map(t => ({ t, ...view(t) })) })))
</script>

<template>
  <aside class="flex flex-col gap-2.5 border-r border-line-1 bg-surface-1 px-3 py-4 max-xl:border-r-0 max-xl:border-b max-xl:px-4">
    <div class="contents max-xl:flex max-xl:items-center max-xl:gap-3 max-md:flex-wrap max-md:gap-y-2.5">
    <div class="flex items-baseline justify-between px-1.5 max-xl:shrink-0 max-xl:gap-3 max-md:basis-full max-md:justify-start">
      <span class="text-[15px] font-semibold">发布任务</span>
      <span class="text-xs text-ink-4">{{ s.queueCount }} 项</span>
    </div>
    <div class="relative max-xl:min-w-0 max-xl:flex-1 max-md:basis-full">
      <input
        v-model="s.q"
        placeholder="搜索发布任务"
        aria-label="搜索发布任务"
        class="h-[34px] w-full max-xl:h-11 rounded-lg border border-line-1 bg-white pr-2.5 pl-8 text-[13px] outline-none placeholder:text-ink-5 focus:border-brand-line"
      >
      <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="#98A2B3" stroke-width="2" stroke-linecap="round" class="pointer-events-none absolute top-2.5 left-2.5 max-xl:top-1/2 max-xl:-translate-y-1/2" aria-hidden="true"><path d="M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.3-4.3" /></svg>
    </div>
    <div class="flex rounded-lg bg-line-2 p-[3px] max-xl:min-w-0 max-xl:flex-1 max-md:basis-full" role="group" aria-label="任务筛选">
      <button type="button"
        v-for="t in s.filterCounts"
        :key="t.l"
        :aria-pressed="t.l === s.qf"
        :class="cn(
          'flex-1 cursor-pointer rounded-md py-[5px] text-center text-xs whitespace-nowrap max-xl:min-h-10 max-xl:px-2',
          t.l === s.qf ? 'bg-white text-ink-1 shadow-[0_1px_2px_rgba(15,23,42,.1)]' : 'text-ink-4',
        )"
        @click="s.qf = t.l"
      >{{ t.l }} <span :class="cn('yb-num', t.l === s.qf ? 'text-brand' : 'text-ink-5')">{{ t.n }}</span></button>
    </div>
    </div>
    <div class="flex flex-col gap-2.5 max-xl:flex-row max-xl:gap-5 max-xl:overflow-x-auto max-xl:pb-1">
    <div v-for="g in groups" :key="g.g" class="mt-1 flex flex-col gap-1 max-xl:shrink-0">
      <div class="px-1.5 text-[11px] font-semibold tracking-[.5px] text-ink-5">{{ g.g }}</div>
      <div class="flex flex-col gap-1 max-xl:flex-row max-xl:gap-2">
        <div v-press
          v-for="v in g.items"
          :key="v.t.id"
          :class="cn(
            'flex cursor-pointer flex-col gap-1.5 rounded-[10px] border px-3 py-2.5 hover:bg-white max-xl:w-[250px] max-xl:shrink-0 max-xl:bg-white max-xl:py-3',
            v.on ? 'border-brand-line bg-white shadow-[0_1px_3px_rgba(15,23,42,.08)]' : 'border-transparent',
          )"
          @click="s.selectTask(v.t.id)"
        >
          <div class="flex items-start gap-2">
            <span class="mt-1.5 size-2 shrink-0 rounded-full" :style="{ background: v.dot }" />
            <span class="min-w-0 flex-1 text-[13px] font-medium">{{ v.t.name }}</span>
          </div>
          <div class="flex justify-between pl-4 text-[11px] whitespace-nowrap">
            <span class="font-medium" :style="{ color: v.lc }">{{ v.lbl }}</span>
            <span :class="v.ur ? 'font-semibold text-bad-ink' : 'text-ink-5'">{{ v.due }}</span>
          </div>
          <div class="flex gap-0.5 pl-4">
            <span v-for="(x, i) in v.segs" :key="i" class="h-[3px] flex-1 rounded-[2px]" :style="{ background: x }" />
          </div>
        </div>
      </div>
    </div>
    </div>
    <div v-if="s.queueCount === 0" class="px-2 py-7 text-center text-xs text-ink-5">没有匹配的发布任务</div>
  </aside>
</template>
