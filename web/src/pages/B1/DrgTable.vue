<script setup lang="ts">
import { computed, ref } from 'vue'
import { cn } from '@/lib/utils'
import { fmt, sign } from '@/lib/format'
import { A, GT, R, BRAND } from '@/lib/palette'
import { goPage } from '@/app/router'
import type { B1Data, B1Drg } from '@/mock/B1'
import { PERCENTILE_HINT, WATCH_BELOW } from './percentile'
import { vPress } from '@/lib/a11y'

/** 重点病组明细: top-8 by 差额总额, sticky header (under the 60px app header), click-to-sort. */
const props = defineProps<{ drgs: B1Drg[]; totalCases: number; settlement: B1Data['settlement'] }>()
const selected = defineModel<string>('selected', { required: true })

type SortKey = 'k' | 'n' | 'c' | 'df' | 'city' | 'p' | 'tot'
const sort = ref<{ k: SortKey; d: 1 | -1 }>({ k: 'tot', d: -1 })
const KF: Record<SortKey, (x: B1Drg) => number | string> = {
  k: x => x.code,
  n: x => x.name,
  c: x => x.cases,
  df: x => x.diff,
  city: x => x.cityDiff,
  p: x => x.percentile,
  tot: x => x.cases * x.diff,
}

const COLS: [string, SortKey, 'start' | 'end'][] = [
  ['编码', 'k', 'start'], ['病组', 'n', 'start'], ['本院病例', 'c', 'end'],
  ['例均差额', 'df', 'end'], ['全市同组', 'city', 'end'], ['同级分位', 'p', 'start'],
]
const head = computed(() =>
  COLS.map(([l, k, jc]) => {
    const on = sort.value.k === k
    return { l, k, jc, on, ar: on ? (sort.value.d > 0 ? '▲' : '▼') : '↕' }
  }),
)
const toggle = (k: SortKey) => {
  const on = sort.value.k === k
  sort.value = { k, d: on ? (sort.value.d === 1 ? -1 : 1) : -1 }
}

const rows = computed(() => {
  const { k, d } = sort.value
  return [...props.drgs]
    .sort((a, b) => b.cases * b.diff - a.cases * a.diff)
    .slice(0, 8)
    .sort((a, b) => {
      const x = KF[k](a), y = KF[k](b)
      return (typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y))) * d
    })
    .map(x => {
      const small = x.cases < 30
      return {
        k: x.code,
        n: x.name,
        c: small ? '< 30' : fmt(x.cases),
        df: small ? '并入其他' : sign(x.diff),
        dfc: small ? '#98A2B3' : x.diff > 0 ? R : GT,
        city: sign(x.cityDiff),
        p: small ? '—' : 'P' + x.percentile,
        pl: x.percentile + '%',
        pc: x.percentile < WATCH_BELOW ? A : BRAND,
        big: !small,
        tr: x.trend.map(t => ({ h: 6 + (t + 3) * 3 + 'px', c: t > 0 ? '#F3B4AE' : '#A9DCC4' })),
      }
    })
})

/** reconciliation: listed groups + 其余病组 = 全院 (cases) and = 偏离 (差额总额, 万元) */
const recon = computed(() => {
  const listedCases = props.drgs.reduce((a, x) => a + x.cases, 0)
  const listed = props.drgs.reduce((a, x) => a + x.cases * x.diff, 0) / 10000
  const total = props.settlement.billed - props.settlement.drgPaid
  const s1 = (v: number) => (v >= 0 ? '+' : '−') + Math.abs(v).toFixed(1) + ' 万'
  return {
    n: props.drgs.length,
    listedCases: fmt(listedCases),
    listed: s1(listed),
    otherCases: fmt(Math.max(0, props.totalCases - listedCases)),
    other: s1(total - listed),
    totalCases: fmt(props.totalCases),
    total: s1(total),
  }
})

const GRID = 'grid grid-cols-[56px_minmax(180px,1.6fr)_80px_100px_100px_160px_110px_80px] gap-3.5 px-5'
</script>

<template>
  <div class="yb-card overflow-clip">
    <div class="flex justify-between border-b border-line-2 px-card-x py-3.5">
      <span class="text-[15px] font-semibold">重点病组明细</span>
      <span class="text-[12px] text-ink-4">差额总额前 8 · 病例 &lt; 30 并入其他 · <span :title="PERCENTILE_HINT">同级分位越高越好</span></span>
    </div>
    <div class="max-xl:overflow-x-auto">
    <div class="max-xl:min-w-[1010px]">
    <div :class="cn(GRID, 'sticky top-(--sticky-top) z-[6] bg-surface-1 py-[9px] text-[12px] text-ink-4')">
      <button type="button"
        v-for="c in head" :key="c.k"
        :class="cn(
          'flex cursor-pointer items-center gap-[3px] whitespace-nowrap max-xl:min-h-10',
          c.jc === 'end' ? 'justify-end' : 'justify-start',
          c.on ? 'font-semibold text-ink-1' : 'font-normal text-ink-4',
        )"
        @click="toggle(c.k)"
      >{{ c.l }}<span class="text-[9px]" :class="c.on ? 'opacity-100' : 'opacity-35'">{{ c.ar }}</span></button>
      <span /><span />
    </div>
    <div v-press
      v-for="r in rows" :key="r.k"
      :class="cn(
        GRID,
        'cursor-pointer items-center yb-tr border-b border-line-3 py-[calc(var(--row-py)-1px)] text-[13px] hover:bg-surface-1',
        r.k === selected ? 'bg-brand-tint' : 'bg-white',
      )"
      @click="selected = r.k"
    >
      <span class="yb-num font-semibold text-brand">{{ r.k }}</span>
      <span>{{ r.n }}</span>
      <span class="yb-num text-right">{{ r.c }}</span>
      <span class="yb-num text-right font-semibold" :style="{ color: r.dfc }">{{ r.df }}</span>
      <span class="yb-num text-right text-ink-4">{{ r.city }}</span>
      <div class="flex items-center gap-2">
        <div v-if="r.big" class="relative h-1.5 flex-1 rounded-[3px] bg-line-2">
          <div class="absolute inset-y-0 left-1/4 w-1/2 bg-[#DCE5F6]" />
          <div class="absolute -top-[3px] -ml-px h-3 w-[3px] rounded-[1px]" :style="{ left: r.pl, background: r.pc }" />
        </div>
        <span class="yb-num w-[30px] font-semibold" :style="{ color: r.pc }">{{ r.p }}</span>
      </div>
      <div class="flex h-[22px] items-end gap-[3px]">
        <span v-for="(b, i) in r.tr" :key="i" class="w-2 rounded-[1px]" :style="{ height: b.h, background: b.c }" />
      </div>
      <button type="button" class="text-right text-[12px] text-brand max-xl:min-h-10" @click.stop="goPage('B2', { drg: r.k })">下钻 →</button>
    </div>
    </div>
    </div>
    <div class="border-t border-line-2 px-card-x py-2.5 text-[12px] text-ink-4" data-testid="b1-recon">
      本院 {{ recon.n }} 个病组 {{ recon.listedCases }} 例 · 差额合计 {{ recon.listed }};其余病组 {{ recon.otherCases }} 例 · {{ recon.other }};
      全院入组 {{ recon.totalCases }} 例 · 差额合计 {{ recon.total }}(= 医保记账 − DRG 支付)
    </div>
  </div>
</template>
