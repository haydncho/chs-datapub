<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { fmt, sign } from '@/lib/format'
import { G, GT, R } from '@/lib/palette'
import { goPage } from '@/app/router'
import type { B1Data } from '@/mock/B1'
import { vPress } from '@/lib/a11y'

/** 本院病组全景: own DRGs (coloured) over the grey city peer-average layer. */
const props = defineProps<{ data: B1Data }>()
const selected = defineModel<string>('selected', { required: true })

const X_MAX = 500
const Y_MAX = 3200
const pos = (x: number, y: number) => ({
  x: (Math.min(x / X_MAX, 1) * 100).toFixed(2),
  y: (50 - Math.max(-1, Math.min(1, y / Y_MAX)) * 50).toFixed(2),
})
const radius = (cost: number) => 5 + Math.sqrt(cost) / 24

const yTicks = [1, 0.5, 0, -0.5, -1].map(f => ({ p: (50 - f * 50).toFixed(1), l: f === 0 ? '0' : sign(Math.round(f * Y_MAX)) }))
const xTicks = [0, 100, 200, 300, 400, 500].map(t => ({ p: t / 5, l: t }))

const gray = computed(() =>
  props.data.drgs.map(d => {
    const r = radius(d.cost)
    return { k: d.code, ...pos(d.cityCases * 0.22, d.cityDiff), d: Math.round(r * 2), m: -Math.round(r) }
  }),
)

const own = computed(() =>
  props.data.drgs.map(o => {
    const r = radius(o.cost)
    const on = o.code === selected.value
    return {
      k: o.code,
      ...pos(o.cases, o.diff),
      d: Math.round(r * 2),
      m: -Math.round(r),
      fill: o.cases < 30 ? '#C3CAD6' : o.diff > 0 ? R : G,
      op: Math.abs(o.diff) < 150 ? 0.5 : 0.85,
      bd: on ? '2px solid #0F1A2E' : '1.5px solid #fff',
      z: on ? 50 : 10,
      tip: o.code + ' ' + o.name,
    }
  }),
)

const labels = computed(() =>
  [...props.data.drgs]
    .sort((a, b) => b.cases * b.diff - a.cases * a.diff)
    .slice(0, 3)
    .map(o => {
      const r = radius(o.cost)
      const p = pos(o.cases, o.diff)
      const left = +p.x > 65
      return { k: o.code, ...p, off: left ? -(r + 6) : r + 6, tf: left ? 'translateX(-100%)' : 'none', t: o.code + ' ' + o.name.split(',')[0] }
    }),
)

const median = computed(() => {
  const xs = props.data.drgs.map(o => o.cases).sort((a, b) => a - b)
  return ((xs[Math.floor(xs.length / 2)] ?? 0) / X_MAX * 100).toFixed(1)
})

const sel = computed(() => {
  const o = props.data.drgs.find(x => x.code === selected.value) ?? props.data.drgs[0]!
  const tot = o.cases * o.diff
  return {
    k: o.code,
    n: o.name,
    c: fmt(o.cases),
    df: sign(o.diff),
    dfc: o.diff > 0 ? R : GT,
    city: sign(o.cityDiff),
    cc: o.cityDiff > 0 ? R : GT,
    tot: (o.diff > 0 ? '逆差 ' : '结余 ') + (Math.abs(tot) / 10000).toFixed(1) + ' 万',
    totc: o.diff > 0 ? R : GT,
  }
})

const lin = computed(() => props.data.lineage)
</script>

<template>
  <div class="yb-card px-card-x py-card-y">
    <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
      <span class="flex items-center gap-2">
        <span class="text-[15px] font-semibold">本院病组全景</span>
        <Popover>
          <PopoverTrigger as-child>
            <button
              type="button"
              title="查看口径与数据血缘"
              aria-label="查看口径与数据血缘"
              class="relative cursor-pointer rounded-full bg-surface-3 px-2 py-px font-mono text-[11px] whitespace-nowrap max-xl:py-0.5 max-xl:text-[12px] max-xl:before:absolute max-xl:before:-inset-x-1 max-xl:before:-inset-y-3 max-xl:before:content-[''] text-ink-3 hover:bg-brand-soft hover:text-brand data-[state=open]:bg-brand-soft data-[state=open]:text-brand"
            >口径 {{ lin.version }} · {{ lin.batch }}</button>
          </PopoverTrigger>
          <PopoverContent align="start" class="w-[280px] rounded-[10px] border-line-1 px-4 py-3 text-[12px] shadow-[0_8px_24px_rgba(11,21,38,.12)]">
            <div class="mb-2 text-[13px] font-semibold">口径与数据血缘</div>
            <div class="grid grid-cols-[56px_minmax(0,1fr)] gap-x-3 gap-y-1.5">
              <span class="text-ink-4">指标卡</span><span class="font-medium text-brand">{{ lin.indicator }} {{ lin.version }}</span>
              <span class="text-ink-4">取数批次</span><span class="font-mono text-ink-2">{{ lin.batch }}</span>
              <span class="text-ink-4">来源</span><span class="text-ink-2">{{ lin.source }}</span>
            </div>
          </PopoverContent>
        </Popover>
      </span>
      <span class="text-[12px] whitespace-nowrap text-ink-4">彩色 本院 · 灰色 全市同级同组均值 · 他院不渲染</span>
    </div>

    <div class="relative mt-2.5 h-[400px]">
      <div class="absolute top-2 right-3 bottom-8 left-[60px] isolate">
        <div class="absolute inset-x-0 top-0 h-1/2 bg-[#FDF7F6]" />
        <div class="absolute inset-x-0 bottom-0 h-1/2 bg-[#F5FBF8]" />
        <div v-for="t in yTicks" :key="'y' + t.p" class="absolute inset-x-0 border-t border-line-2" :style="{ top: t.p + '%' }">
          <span class="yb-num absolute right-full -top-[9px] mr-2 text-[12px] whitespace-nowrap text-ink-5">{{ t.l }}</span>
        </div>
        <div v-for="t in xTicks" :key="'x' + t.p" class="absolute inset-y-0 border-l border-[#F4F6F9]" :style="{ left: t.p + '%' }">
          <span class="yb-num absolute top-full -left-5 mt-1.5 w-10 text-center text-[12px] text-ink-5">{{ t.l }}</span>
        </div>
        <div class="absolute inset-x-0 top-1/2 border-t-[1.5px] border-dashed border-ink-4" />
        <div class="absolute inset-y-0 border-l-[1.5px] border-dashed border-brand-line" :style="{ left: median + '%' }" />
        <span class="absolute top-1.5 right-2 text-[12px] font-semibold text-bad-ink">高量 · 逆差</span>
        <span class="absolute top-1.5 left-2 text-[12px] text-[#C9877F]">低量 · 逆差</span>
        <span class="absolute right-2 bottom-1.5 text-[12px] text-ok-ink">高量 · 结余</span>
        <span class="absolute bottom-1.5 left-2 text-[12px] text-[#5A9C84]">低量 · 结余</span>
        <div
          v-for="b in gray" :key="'g' + b.k"
          class="absolute rounded-full bg-[#E4E8EF]"
          :style="{ left: b.x + '%', top: b.y + '%', width: b.d + 'px', height: b.d + 'px', marginLeft: b.m + 'px', marginTop: b.m + 'px' }"
        />
        <div v-press
          v-for="b in own" :key="'o' + b.k"
          :title="b.tip"
          class="absolute box-content cursor-pointer rounded-full max-xl:before:absolute max-xl:before:-inset-3 max-xl:before:content-['']"
          :style="{
            left: b.x + '%', top: b.y + '%', width: b.d + 'px', height: b.d + 'px',
            marginLeft: b.m + 'px', marginTop: b.m + 'px',
            background: b.fill, opacity: b.op, border: b.bd, zIndex: b.z,
          }"
          @click="selected = b.k"
        />
        <div
          v-for="b in labels" :key="'l' + b.k"
          class="pointer-events-none absolute z-[60] -mt-2.5 rounded bg-white/92 px-1.5 text-[12px] font-semibold whitespace-nowrap"
          :style="{ left: b.x + '%', top: b.y + '%', marginLeft: b.off + 'px', transform: b.tf }"
        >{{ b.t }}</div>
      </div>
      <span class="absolute right-3 bottom-0 text-[11px] text-ink-5 max-xl:text-[12px]">本院病例数 →</span>
    </div>

    <div class="mt-3 items-center gap-[22px] rounded-[10px] bg-surface-1 px-4 py-3 max-xl:grid max-xl:grid-cols-2 max-xl:gap-x-4 max-xl:gap-y-3 md:max-xl:grid-cols-4 xl:flex">
      <div class="min-w-0 flex-[1.4] max-xl:col-span-full">
        <div class="yb-num font-semibold text-brand">{{ sel.k }}</div>
        <div class="truncate font-semibold" :title="sel.n">{{ sel.n }}</div>
      </div>
      <div class="flex-1">
        <div class="text-[11px] text-ink-4 max-xl:text-[12px]">本院病例</div>
        <div class="yb-num text-lg font-semibold">{{ sel.c }}</div>
      </div>
      <div class="flex-1">
        <div class="text-[11px] text-ink-4 max-xl:text-[12px]">例均差额</div>
        <div class="yb-num text-lg font-semibold" :style="{ color: sel.dfc }">{{ sel.df }}</div>
      </div>
      <div class="flex-1">
        <div class="text-[11px] text-ink-4 max-xl:text-[12px]">全市同组</div>
        <div class="yb-num text-lg font-semibold" :style="{ color: sel.cc }">{{ sel.city }}</div>
      </div>
      <div class="flex-[1.3] whitespace-nowrap">
        <div class="text-[11px] text-ink-4 max-xl:text-[12px]">差额总额</div>
        <div class="yb-num text-lg font-semibold" :style="{ color: sel.totc }">{{ sel.tot }}</div>
      </div>
      <Button variant="outline" class="h-[34px] px-3.5 font-normal max-xl:col-span-full max-xl:h-10" @click="goPage('B2', { drg: sel.k })">病组下钻 →</Button>
    </div>
  </div>
</template>
