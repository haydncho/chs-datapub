<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import type { A4Group, A4Indicator, A4Source } from '@/mock/A4'
import { GROUPS, GROUP_COLOR, GROUP_NAME, SOURCE_FILTERS } from './meta'
import { vPress } from '@/lib/a11y'

const props = defineProps<{
  indicators: A4Indicator[]
  grp: A4Group | '全部'
  dom: string | null
  isrc: string
}>()
const emit = defineEmits<{ filter: [grp: A4Group | '全部', dom: string | null]; source: [label: string] }>()

const srcKey = computed<A4Source | null>(() => SOURCE_FILTERS.find(f => f.label === props.isrc)?.source ?? null)
const srcOk = (r: A4Indicator) => !srcKey.value || r.source === srcKey.value

const allOn = computed(() => props.grp === '全部' && !props.dom)
const allCount = computed(() => props.indicators.filter(srcOk).length)

const tree = computed(() =>
  GROUPS.map(g => {
    const list = props.indicators.filter(r => r.group === g && srcOk(r))
    const domains = [...new Set(props.indicators.filter(r => r.group === g).map(r => r.domain))]
    return {
      g,
      name: GROUP_NAME[g],
      color: GROUP_COLOR[g],
      on: props.grp === g && !props.dom,
      count: list.length,
      kids: domains.map(d => ({ d, count: list.filter(r => r.domain === d).length, on: props.dom === d })),
    }
  }),
)

const chips = computed(() =>
  SOURCE_FILTERS.map(f => ({
    label: f.label,
    on: props.isrc === f.label,
    n: f.source ? props.indicators.filter(r => r.source === f.source).length : props.indicators.length,
  })),
)
</script>

<template>
  <aside class="flex flex-col gap-0.5 border-r border-line-1 bg-surface-1 px-3 py-5">
    <div class="px-2.5 pb-2 text-[11px] font-semibold tracking-[.5px] text-ink-5">指标目录 · 按监测维度</div>
    <div v-press
      :class="cn(
        'flex cursor-pointer justify-between rounded-lg px-2.5 py-[7px]',
        allOn ? 'bg-brand-soft font-semibold text-brand' : 'font-medium text-ink-2',
      )"
      @click="emit('filter', '全部', null)"
    >
      <span>全部指标</span><span class="yb-num text-ink-5">{{ allCount }}</span>
    </div>
    <div v-for="g in tree" :key="g.g" class="mt-1.5 flex flex-col gap-px">
      <div v-press
        :class="cn(
          'flex cursor-pointer items-center gap-2 rounded-lg px-2.5 py-[7px] font-semibold',
          g.on ? 'bg-brand-soft text-brand' : 'text-ink-1',
        )"
        @click="emit('filter', g.g, null)"
      >
        <span class="size-2 rounded-[2px]" :style="{ background: g.color }" />
        <span class="flex-1 whitespace-nowrap">{{ g.name }}</span>
        <span class="yb-num font-normal text-ink-5">{{ g.count }}</span>
      </div>
      <div v-press
        v-for="k in g.kids"
        :key="k.d"
        :class="cn(
          'ml-3.5 flex cursor-pointer items-center justify-between rounded-r-md border-l-[1.5px] py-[5px] pr-2.5 pl-3 text-xs hover:bg-[#EEF2F7]',
          k.on ? 'bg-white font-semibold' : 'border-line-1 font-normal text-ink-3',
        )"
        :style="k.on ? { borderLeftColor: g.color, color: g.color } : undefined"
        @click="emit('filter', g.g, k.d)"
      >
        <span class="whitespace-nowrap">{{ k.d }}</span>
        <span class="yb-num text-ink-5">{{ k.count }}</span>
      </div>
    </div>
    <div class="mt-3.5 border-t border-line-1 px-2.5 pt-[18px] pb-2 text-[11px] font-semibold tracking-[.5px] text-ink-5">指标来源</div>
    <div class="flex flex-wrap gap-1.5 px-1.5">
      <button type="button"
        v-for="c in chips"
        :key="c.label"
        :class="cn(
          'cursor-pointer rounded-full border px-2.5 py-[3px] text-xs whitespace-nowrap',
          c.on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
        )"
        @click="emit('source', c.label)"
      >{{ c.label }} <span class="yb-num opacity-70">{{ c.n }}</span></button>
    </div>
  </aside>
</template>
