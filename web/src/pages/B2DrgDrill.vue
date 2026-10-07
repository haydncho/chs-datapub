<script setup lang="ts">
import { computed, ref } from 'vue'
import { PageSection } from '@/components/yb'
import { cn } from '@/lib/utils'
import { fmt, sign, splitUnit } from '@/lib/format'
import { A, AS, BRAND, BRAND_SOFT, G, GS, GT, INK, R, RS } from '@/lib/palette'
import { goPage } from '@/app/router'
import { usePageData } from '@/api/client'
import { B2_SEED, type B2Group } from '@/mock/B2'

const data = usePageData('B2', B2_SEED)
const grp = ref('BR25')
const g = computed<B2Group>(() => data.value.groups.find(x => x.code === grp.value) ?? data.value.groups[0]!)

const ICONS = [
  'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21c0-3.9 3.1-7 7-7s7 3.1 7 7M16 3.5a4 4 0 0 1 0 7.5M22 21c0-3.2-2-5.8-5-6.7',
  'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9 10h6M9 14h6M12 10v7',
  'M3 17l6-6 4 4 8-8M15 7h6v6',
  'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  'M6 3h9l5 5v13H6zM14 3v6h6M9 13h7M9 17h5',
]

const kpis = computed(() => {
  const x = g.value
  const losD = x.los - x.cityLos
  const rows: [string, string, string, string, boolean][] = [
    ['本院病例', fmt(x.cases), fmt(x.cityCases), '', false],
    ['例均费用', fmt(x.avgCost), fmt(x.cityAvgCost), sign(x.avgCost - x.cityAvgCost), true],
    ['例均基金差额', sign(x.avgDiff), sign(x.cityAvgDiff), '同级 P' + x.peerPercentile, true],
    ['平均住院日', x.los + ' 天', x.cityLos.toFixed(1) + ' 天', (losD >= 0 ? '+' : '−') + Math.abs(losD).toFixed(1) + ' 天', true],
    ['合并症编码率', x.comorbidityRate, x.cityComorbidityRate, '', false],
  ]
  const tones: [string, string, string][] = [
    [BRAND_SOFT, '#DCE6F8', BRAND],
    [AS, '#F6DFB8', A],
    x.avgDiff > 0 ? [RS, '#F5CFCB', R] : [GS, '#CBEBDB', G],
    [AS, '#F6DFB8', A],
    [GS, '#CBEBDB', G],
  ]
  return rows.map(([k, v, city, d, hot], i) => {
    const [bg, bd, ic] = tones[i]!
    return {
      k, city, d, ...splitUnit(v), bg, bd, ic, ip: ICONS[i]!,
      c: i === 2 ? (x.avgDiff > 0 ? R : GT) : INK[0],
      dc: hot ? R : INK[3],
    }
  })
})

const hist = computed(() => {
  const h = g.value.histogram
  const mh = Math.max(...h)
  return h.map((v, i) => ({ h: ((v / mh) * 100).toFixed(0) + '%', c: i < 6 ? '#9DBCE9' : BRAND }))
})
const stdX = computed(() => ((data.value.standard / data.value.histMax) * 100).toFixed(1) + '%')

const structure = computed(() => {
  const s = g.value.structure
  const mx = Math.max(...s.map(x => Math.max(x.hospital, x.city)))
  return s.map(x => {
    const d = x.hospital - x.city
    return {
      k: x.item,
      aw: ((x.hospital / mx) * 100).toFixed(1) + '%',
      bw: ((x.city / mx) * 100).toFixed(1) + '%',
      d: sign(d),
      dc: d > 0 ? 'text-bad' : 'text-ok-ink',
    }
  })
})

const teams = computed(() =>
  data.value.teams.map(t => ({
    ...t,
    cost: fmt(t.avgCost),
    d: sign(t.avgDiff),
    dc: t.avgDiff > 2000 ? 'text-bad' : t.avgDiff > 0 ? 'text-warn-ink' : 'text-ok-ink',
    rw: (t.repeatRate / 50) * 100 + '%',
    rc: t.repeatRate >= 35 ? R : t.repeatRate >= 25 ? A : BRAND,
  })),
)

const TEAM_COLS = 'grid grid-cols-[minmax(120px,1fr)_70px_100px_100px_90px_minmax(160px,1.2fr)] gap-3.5 px-5'
</script>

<template>
  <PageSection label="B2 病组下钻">
    <div class="flex flex-wrap items-end justify-between gap-x-5 gap-y-3">
      <div>
        <div class="text-xs text-ink-4">
          <button type="button" class="cursor-pointer text-brand" @click="goPage('B1')">本院全景</button> / 病组下钻
        </div>
        <div class="mt-0.5 text-2xl font-semibold">
          <span class="yb-num text-brand">{{ data.header.code }}</span> {{ data.header.name }}
        </div>
        <div class="text-[13px] text-ink-4">{{ data.header.meta }}</div>
      </div>
      <div class="flex gap-1.5 max-xl:gap-2">
        <button type="button"
          v-for="x in data.groups"
          :key="x.code"
          :class="cn(
            'yb-num cursor-pointer rounded-lg border px-3 py-1.5 text-xs font-semibold max-xl:min-h-10 max-xl:min-w-14',
            x.code === grp ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
          )"
          @click="grp = x.code"
        >{{ x.code }}</button>
      </div>
    </div>

    <!-- KPI row -->
    <div class="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-5 max-lg:[&>*:last-child]:col-span-2">
      <div
        v-for="k in kpis"
        :key="k.k"
        class="yb-stat relative flex items-start gap-3 overflow-hidden rounded-xl border px-[18px] py-card-y-sm"
        :style="{ '--stat-g': k.bg, '--stat-bd': k.bd, '--stat-stop': '64%' }"
      >
        <div class="min-w-0 flex-1 whitespace-nowrap">
          <div class="text-xs text-ink-4">{{ k.k }}</div>
          <div class="yb-num text-[26px] font-semibold" :style="{ color: k.c }">
            {{ k.vn }}<span v-if="k.vu" class="ml-[3px] text-[13px] font-medium text-ink-4">{{ k.vu }}</span>
          </div>
          <div class="text-xs text-ink-4">全市 {{ k.city }} · <span :style="{ color: k.dc }">{{ k.d }}</span></div>
        </div>
        <span
          class="flex size-[34px] shrink-0 items-center justify-center rounded-[10px] bg-white"
          :style="{ boxShadow: `0 0 0 1px ${k.bd}` }"
        >
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" :stroke="k.ic" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="k.ip" /></svg>
        </span>
        <svg viewBox="0 0 24 24" width="92" height="92" fill="none" :stroke="k.ic" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" class="pointer-events-none absolute -right-[18px] -bottom-[26px] opacity-[.07]"><path :d="k.ip" /></svg>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
      <!-- cost distribution -->
      <div class="yb-card px-card-x py-card-y">
        <div class="flex flex-wrap justify-between gap-x-4 gap-y-1">
          <span class="flex items-center gap-2">
            <span class="text-[15px] font-semibold">病例费用分布</span>
            <span class="rounded-full bg-surface-3 px-2 py-px font-mono text-[11px] whitespace-nowrap text-ink-3">{{ data.basis }}</span>
          </span>
          <span class="text-xs whitespace-nowrap text-ink-4">{{ data.histNote }}</span>
        </div>
        <div class="relative mt-3.5 flex h-[220px] items-end gap-1 border-b border-line-4">
          <div
            v-for="(h, i) in hist"
            :key="i"
            class="flex-1 rounded-t-[3px]"
            :style="{ height: h.h, background: h.c }"
          />
          <div class="absolute -top-1.5 bottom-0 border-l-2 border-dashed border-ink-1" :style="{ left: stdX }">
            <span class="absolute top-0 left-1.5 text-[11px] font-semibold whitespace-nowrap">支付标准 {{ fmt(data.standard) }}</span>
          </div>
        </div>
        <div class="yb-num mt-1.5 flex justify-between text-[11px] text-ink-5">
          <span v-for="a in data.histAxis" :key="a">{{ a }}</span>
        </div>
        <div class="mt-3 flex gap-5 text-xs whitespace-nowrap">
          <span><b class="yb-num text-base text-ok-ink">{{ data.histSummary.below }}</b> 低于标准</span>
          <span><b class="yb-num text-base text-bad">{{ data.histSummary.above }}</b> 高于标准</span>
          <span><b class="yb-num text-base">{{ data.histSummary.highMultiple }}</b> 例高倍率(&gt;2 倍)</span>
        </div>
      </div>

      <!-- cost structure -->
      <div class="yb-card px-card-x py-card-y">
        <div class="mb-3 flex justify-between">
          <span class="text-[15px] font-semibold">费用结构 · 本院 vs 全市</span>
          <span class="text-xs text-ink-4">例均 · 元</span>
        </div>
        <div v-for="r in structure" :key="r.k" class="grid grid-cols-[72px_1fr_64px] items-center gap-2.5 py-[13px] text-xs">
          <span class="text-ink-3">{{ r.k }}</span>
          <div class="flex flex-col gap-[3px]">
            <div class="h-3 rounded-[2px] bg-brand" :style="{ width: r.aw }" />
            <div class="h-3 rounded-[2px] bg-[#C9D3E1]" :style="{ width: r.bw }" />
          </div>
          <span :class="cn('yb-num text-right font-semibold', r.dc)">{{ r.d }}</span>
        </div>
        <div class="mt-1.5 flex gap-3.5 text-[11px] text-ink-4">
          <span class="flex items-center gap-1"><span class="h-2 w-2.5 rounded-[2px] bg-brand" />本院</span>
          <span class="flex items-center gap-1"><span class="h-2 w-2.5 rounded-[2px] bg-[#C9D3E1]" />全市同组</span>
        </div>
      </div>
    </div>

    <!-- teams -->
    <div class="yb-card overflow-hidden">
      <div class="flex justify-between border-b border-line-2 px-card-x py-3.5">
        <span class="text-[15px] font-semibold">本院医疗组</span>
        <span class="text-xs text-ink-4">{{ data.teamsNote }}</span>
      </div>
      <div class="max-xl:overflow-x-auto">
      <div class="max-xl:min-w-[760px]">
      <div :class="cn(TEAM_COLS, 'bg-surface-1 py-[9px] text-xs text-ink-4')">
        <span>医疗组</span><span class="text-right">病例</span><span class="text-right">例均费用</span>
        <span class="text-right">例均差额</span><span class="text-right">住院日</span><span>重复检查发生率</span>
      </div>
      <div
        v-for="t in teams"
        :key="t.name"
        :class="cn(TEAM_COLS, 'items-center yb-tr border-b border-line-3 py-row text-[13px]')"
      >
        <span class="font-medium">{{ t.name }}</span>
        <span class="yb-num text-right">{{ t.cases }}</span>
        <span class="yb-num text-right">{{ t.cost }}</span>
        <span :class="cn('yb-num text-right font-semibold', t.dc)">{{ t.d }}</span>
        <span class="yb-num text-right">{{ t.los }}</span>
        <div class="flex items-center gap-2">
          <div class="h-1.5 flex-1 rounded-[3px] bg-line-2">
            <div class="h-1.5 rounded-[3px]" :style="{ width: t.rw, background: t.rc }" />
          </div>
          <span class="yb-num w-10 text-right font-semibold">{{ t.repeatRate }}%</span>
        </div>
      </div>
      </div>
      </div>
    </div>
  </PageSection>
</template>
