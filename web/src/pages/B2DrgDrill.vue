<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PageSection, KpiValue } from '@/components/yb'
import { cn } from '@/lib/utils'
import { fmt, sign, splitUnit } from '@/lib/format'
import { A, AS, BRAND, BRAND_SOFT, G, GS, GT, INK, R, RS } from '@/lib/palette'
import { goPage } from '@/app/router'
import { usePageData } from '@/api/client'
import { B2_EMPTY, B2_SEED, type B2Group } from '@/mock/B2'
import OwnDataEmpty from './B1/OwnDataEmpty.vue'
import { ownSeed } from './B1/ownSeed'
import { PERCENTILE_HINT, WATCH_BELOW } from './B1/percentile'

const data = usePageData('B2', ownSeed(B2_SEED, B2_EMPTY))
const route = useRoute()
const router = useRouter()

/** selected DRG: ?drg= from B1's 下钻, else the default group */
const grp = computed(() => {
  const q = route.query.drg
  const want = typeof q === 'string' ? q.toUpperCase() : ''
  const codes = data.value.groups.map(x => x.code)
  return codes.includes(want) ? want : codes.includes(data.value.defaultGroup) ? data.value.defaultGroup : (codes[0] ?? '')
})
const g = computed<B2Group | undefined>(() => data.value.groups.find(x => x.code === grp.value))
/** a ?drg= that this institution has no drill data for */
const missing = computed(() => {
  const q = route.query.drg
  return typeof q === 'string' && q !== '' && q.toUpperCase() !== grp.value ? q : ''
})

/** quick-switch chips: the first six groups, plus the selected one when it is not among them */
const chips = computed(() => {
  const top = data.value.groups.slice(0, 6)
  return g.value && !top.includes(g.value) ? [...top, g.value] : top
})

function choose(code: string) {
  void router.replace({ query: { ...route.query, drg: code } })
}

const ICONS = [
  'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21c0-3.9 3.1-7 7-7s7 3.1 7 7M16 3.5a4 4 0 0 1 0 7.5M22 21c0-3.2-2-5.8-5-6.7',
  'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9 10h6M9 14h6M12 10v7',
  'M3 17l6-6 4 4 8-8M15 7h6v6',
  'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  'M6 3h9l5 5v13H6zM14 3v6h6M9 13h7M9 17h5',
]

/** colour of a difference: `bad` when the difference is unfavourable for 本院, `good` when favourable */
const tone = (d: number, lowerIsBetter = true) => (d === 0 ? INK[3] : (d > 0) === lowerIsBetter ? R : GT)

const kpis = computed(() => {
  const x = g.value
  if (!x) return []
  const costD = x.avgCost - x.cityAvgCost
  const losD = +(x.los - x.cityLos).toFixed(1)
  const rows: [string, string, string, string, string][] = [
    ['本院病例', fmt(x.cases), '全市同级 ' + fmt(x.cityCases), '', INK[3]],
    ['例均费用', fmt(x.avgCost), '全市 ' + fmt(x.cityAvgCost), sign(costD), tone(costD)],
    ['例均基金差额', sign(x.avgDiff), '全市 ' + sign(x.cityAvgDiff), '同级 P' + x.peerPercentile, x.peerPercentile < WATCH_BELOW ? R : x.peerPercentile >= 50 ? GT : A],
    ['平均住院日', x.los + ' 天', '全市 ' + x.cityLos.toFixed(1) + ' 天', (losD > 0 ? '+' : losD < 0 ? '−' : '') + Math.abs(losD).toFixed(1) + ' 天', tone(losD)],
    ['合并症编码率', x.comorbidityRate, '全市 ' + x.cityComorbidityRate, '', INK[3]],
  ]
  const tones: [string, string, string][] = [
    [BRAND_SOFT, '#DCE6F8', BRAND],
    [AS, '#F6DFB8', A],
    x.avgDiff > 0 ? [RS, '#F5CFCB', R] : [GS, '#CBEBDB', G],
    [AS, '#F6DFB8', A],
    [GS, '#CBEBDB', G],
  ]
  return rows.map(([k, v, city, d, dc], i) => {
    const [bg, bd, ic] = tones[i]!
    return {
      k, city, d, dc, ...splitUnit(v), bg, bd, ic, ip: ICONS[i]!,
      c: i === 2 ? (x.avgDiff > 0 ? R : GT) : INK[0],
      tip: i === 2 ? PERCENTILE_HINT : undefined,
    }
  })
})

/** histogram, axis and the below/above split — all derived from the selected group */
const histo = computed(() => {
  const x = g.value
  if (!x) return null
  const h = x.histogram
  const n = h.length
  const mh = Math.max(...h, 1)
  const total = h.reduce((a, v) => a + v, 0)
  const max = x.bucket * n
  let below = 0
  let high = 0
  h.forEach((v, i) => {
    const lo = i * x.bucket
    const hi = lo + x.bucket
    below += v * Math.max(0, Math.min(1, (x.standard - lo) / x.bucket))
    if (i === n - 1) high += lo >= 2 * x.standard ? v : 0
    else high += v * Math.max(0, Math.min(1, (hi - 2 * x.standard) / x.bucket))
  })
  const belowPct = total ? Math.round((below / total) * 100) : 0
  const k = (v: number) => (v >= 10000 ? (v / 1000).toFixed(0) + 'k' : v >= 1000 ? (v / 1000).toFixed(v % 1000 ? 1 : 0) + 'k' : String(v))
  return {
    bars: h.map((v, i) => ({ h: ((v / mh) * 100).toFixed(0) + '%', c: (i + 1) * x.bucket <= x.standard ? '#9DBCE9' : BRAND, t: `${k(i * x.bucket)}–${i === n - 1 ? '' : k((i + 1) * x.bucket)} 元:${v} 例` })),
    stdX: Math.min(100, (x.standard / max) * 100).toFixed(1) + '%',
    axis: [0, 0.25, 0.5, 0.75, 1].map(f => (f === 1 ? k(max) + '+' : k(Math.round(max * f)))),
    note: `本院 ${fmt(total)} 例 · 每格 ${fmt(x.bucket)} 元`,
    below: belowPct + '%',
    above: 100 - belowPct + '%',
    high: Math.round(high),
  }
})

const structure = computed(() => {
  const s = g.value?.structure ?? []
  const mx = Math.max(1, ...s.map(x => Math.max(x.hospital, x.city)))
  return s.map(x => {
    const d = x.hospital - x.city
    return {
      k: x.item,
      aw: ((x.hospital / mx) * 100).toFixed(1) + '%',
      bw: ((x.city / mx) * 100).toFixed(1) + '%',
      d: sign(d),
      dc: d > 0 ? 'text-bad' : d < 0 ? 'text-ok-ink' : 'text-ink-3',
    }
  })
})

const teams = computed(() =>
  (g.value?.teams ?? []).map(t => ({
    ...t,
    cost: fmt(t.avgCost),
    d: sign(t.avgDiff),
    dc: t.avgDiff > 2000 ? 'text-bad' : t.avgDiff > 0 ? 'text-warn-ink' : 'text-ok-ink',
    rw: Math.min(100, (t.repeatRate / 50) * 100) + '%',
    rc: t.repeatRate >= 35 ? R : t.repeatRate >= 25 ? A : BRAND,
  })),
)

/** Pad: narrower numeric columns so the 医疗组 table fits 768px without a horizontal scroll */
const TEAM_COLS = 'grid grid-cols-[minmax(96px,1fr)_56px_84px_84px_64px_minmax(140px,1.2fr)] gap-3 px-4 xl:grid-cols-[minmax(120px,1fr)_70px_100px_100px_90px_minmax(160px,1.2fr)] xl:gap-3.5 xl:px-5'
</script>

<template>
  <PageSection label="B2 病组下钻">
    <div class="flex flex-wrap items-end justify-between gap-x-5 gap-y-3">
      <div class="min-w-0">
        <div class="text-xs text-ink-4">
          <button type="button" class="relative cursor-pointer text-brand max-xl:before:absolute max-xl:before:-inset-x-2 max-xl:before:-inset-y-3 max-xl:before:content-['']" @click="goPage('B1')">本院全景</button> / 病组下钻
        </div>
        <template v-if="g">
          <h1 class="mt-0.5 text-lg font-semibold" data-testid="b2-title">
            <span class="yb-num text-brand">{{ g.code }}</span> {{ g.name }}
          </h1>
          <div class="text-[13px] text-ink-4" data-testid="b2-meta">
            权重 {{ g.weight.toFixed(2) }} · 支付标准 {{ fmt(g.standard) }} 元 · {{ data.period }} · 本院 {{ fmt(g.cases) }} 例
          </div>
        </template>
        <h1 v-else class="mt-0.5 text-lg font-semibold">病组下钻</h1>
      </div>
      <label v-if="data.groups.length" class="flex items-center gap-2 text-xs text-ink-4">
        病组
        <select
          :value="grp"
          data-testid="b2-group-select"
          class="h-9 max-w-[280px] rounded-lg border border-line-1 bg-white px-2.5 text-[13px] text-ink-1 max-xl:h-10"
          @change="choose(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="x in data.groups" :key="x.code" :value="x.code">{{ x.code }} {{ x.name }}</option>
        </select>
      </label>
    </div>
    <div v-if="data.groups.length" class="-mt-2 flex flex-wrap gap-1.5 max-xl:gap-2" role="group" aria-label="快速切换病组">
      <button type="button"
        v-for="x in chips"
        :key="x.code"
        :aria-pressed="x.code === grp"
        :class="cn(
          'yb-num cursor-pointer rounded-lg border px-3 py-1.5 text-xs font-semibold max-xl:min-h-10 max-xl:min-w-14',
          x.code === grp ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
        )"
        @click="choose(x.code)"
      >{{ x.code }}</button>
    </div>

    <div v-if="missing && g" class="rounded-lg bg-warn-soft px-4 py-2.5 text-[13px] text-warn-ink" role="status">
      本院暂无病组 {{ missing }} 的下钻明细,已显示 {{ g.code }}。
    </div>

    <OwnDataEmpty v-if="!g" :note="data.noOwnDataNote" />

    <template v-else>
      <!-- KPI row -->
      <div class="grid grid-cols-2 gap-3 md:grid-cols-6 xl:grid-cols-5 max-md:[&>*:last-child]:col-span-2">
        <div
          v-for="(k, i) in kpis"
          :key="k.k"
          class="yb-stat relative flex items-start gap-3 overflow-hidden rounded-xl border px-[18px] py-card-y-sm xl:col-span-1"
          :class="i < 3 ? 'md:col-span-2' : 'md:col-span-3'"
          :style="{ '--stat-g': k.bg, '--stat-bd': k.bd, '--stat-stop': '64%' }"
          :title="k.tip"
        >
          <div class="min-w-0 flex-1 whitespace-nowrap">
            <div class="text-xs text-ink-4">{{ k.k }}</div>
            <KpiValue :value="k.vn" :unit="k.vu" class="block" :style="{ color: k.c }" />
            <div class="text-xs text-ink-4">{{ k.city }}<template v-if="k.d"> · <span class="font-semibold" :style="{ color: k.dc }" data-testid="b2-kpi-delta">{{ k.d }}</span></template></div>
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
      <p class="-mt-1 text-[12px] text-ink-4">差值红色 = 本院较全市不利,绿色 = 有利 · 同级分位按表现排位,越高越好</p>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        <!-- cost distribution -->
        <div v-if="histo" class="yb-card px-card-x py-card-y">
          <div class="flex flex-wrap justify-between gap-x-4 gap-y-1">
            <span class="flex items-center gap-2">
              <span class="text-[15px] font-semibold">病例费用分布</span>
              <span class="rounded-full bg-surface-3 px-2 py-px font-mono text-[11px] whitespace-nowrap text-ink-3 max-xl:text-[12px]">{{ data.basis }}</span>
            </span>
            <span class="text-xs whitespace-nowrap text-ink-4" data-testid="b2-hist-note">{{ histo.note }}</span>
          </div>
          <div class="relative mt-3.5 flex h-[220px] items-end gap-1 border-b border-line-4 pt-5">
            <div
              v-for="(h, i) in histo.bars"
              :key="i"
              class="flex-1 rounded-t-[3px]"
              :title="h.t"
              :style="{ height: h.h, background: h.c }"
            />
            <div class="absolute top-0 bottom-0 border-l-2 border-dashed border-ink-1" :style="{ left: histo.stdX }">
              <span class="absolute top-0 left-1.5 text-[11px] font-semibold whitespace-nowrap max-xl:text-[12px]">支付标准 {{ fmt(g.standard) }}</span>
            </div>
          </div>
          <div class="yb-num mt-1.5 flex justify-between text-[11px] text-ink-5 max-xl:text-[12px]">
            <span v-for="a in histo.axis" :key="a">{{ a }}</span>
          </div>
          <div class="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-xs whitespace-nowrap">
            <span><b class="yb-num text-base text-ok-ink">{{ histo.below }}</b> 低于标准</span>
            <span><b class="yb-num text-base text-bad">{{ histo.above }}</b> 高于标准</span>
            <span><b class="yb-num text-base">{{ histo.high }}</b> 例高倍率(&gt;2 倍)</span>
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
          <div class="mt-1.5 flex gap-3.5 text-[11px] text-ink-4 max-xl:text-[12px]">
            <span class="flex items-center gap-1"><span class="h-2 w-2.5 rounded-[2px] bg-brand" />本院</span>
            <span class="flex items-center gap-1"><span class="h-2 w-2.5 rounded-[2px] bg-[#C9D3E1]" />全市同组</span>
          </div>
        </div>
      </div>

      <!-- teams -->
      <div class="yb-card overflow-hidden">
        <div class="flex flex-wrap justify-between gap-x-4 border-b border-line-2 px-card-x py-3.5">
          <span class="text-[15px] font-semibold">本院医疗组 · {{ g.code }}</span>
          <span class="text-xs text-ink-4">{{ data.teamsNote }}</span>
        </div>
        <div class="max-md:overflow-x-auto">
        <div class="max-md:min-w-[640px]" data-testid="b2-teams">
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
    </template>
  </PageSection>
</template>
