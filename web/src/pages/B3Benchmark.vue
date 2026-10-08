<script setup lang="ts">
import { computed } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { cn } from '@/lib/utils'
import { BRAND, BRAND_SOFT, INK } from '@/lib/palette'
import { usePageData } from '@/api/client'
import { B3_EMPTY, B3_SEED, type B3Metric, type B3Tier } from '@/mock/B3'
import OwnDataEmpty from './B1/OwnDataEmpty.vue'
import { ownSeed } from './B1/ownSeed'

const data = usePageData('B3', ownSeed(B3_SEED, B3_EMPTY))

const TIER: Record<B3Tier, { label: string; cls: string }> = {
  pct: { label: '匿名分位', cls: 'bg-brand-soft text-brand' },
  anon: { label: '匿名编号', cls: 'bg-violet-soft text-violet' },
  named: { label: '具名', cls: 'bg-ok-soft text-ok-ink' },
}
const LEGEND: B3Tier[] = ['pct', 'anon', 'named']
const LEGEND_TXT: Record<B3Tier, string> = { pct: '仅见本院位置', anon: '他院以字母代替', named: '显示机构名称' }

const own = computed(() => data.value.ownIndex)
const empty = computed(() => !!data.value.noOwnData || own.value < 0)
const shortName = (n: string) => n.replace('示例市', '')

interface Point { v: number; me: boolean; label: string }

/**
 * Points of one metric. A hospital viewer gets anonymous metrics as `own` + `others` (an unordered
 * distribution — no position is tied to an institution); `values` (aligned with `peers`) only exists
 * for 具名 metrics or in the bureau's full view.
 */
function points(m: B3Metric): Point[] {
  if (m.values) {
    let letter = 0
    return m.values.map((v, i) => {
      const me = i === own.value
      const label = me ? '本院' : m.tier === 'named' ? shortName(data.value.peers[i] ?? '') : String.fromCharCode(65 + letter)
      if (!me) letter++
      return { v, me, label }
    })
  }
  const others = (m.others ?? []).map((v, i) => ({ v, me: false, label: String.fromCharCode(65 + i) }))
  return m.own == null ? others : [...others, { v: m.own, me: true, label: '本院' }]
}

const rows = computed(() =>
  data.value.metrics.map(m => {
    const pts = points(m)
    const vs = pts.map(p => p.v)
    const mn = Math.min(...vs)
    const mx = Math.max(...vs)
    const dots = pts.map(p => {
      let pos = (p.v - mn) / (mx - mn || 1)
      if (!m.higherIsBetter) pos = 1 - pos
      const show = m.tier !== 'pct' || p.me
      return {
        x: (4 + pos * 92).toFixed(1) + '%',
        s: p.me ? '16px' : '12px',
        m: p.me ? '-8px' : '-6px',
        t: p.me ? '9px' : '11px',
        c: p.me ? BRAND : show ? '#9DB0CC' : '#DCE1E9',
        sh: p.me ? `0 0 0 3px ${BRAND_SOFT}` : 'none',
        z: p.me ? 5 : 1,
        show,
        alt: false,
        l: p.label,
        lc: p.me ? BRAND : INK[3],
        lw: p.me ? 700 : 400,
        val: p.v,
      }
    })
    // stagger labels of named metrics so neighbouring names do not overlap on narrow screens:
    // 本院 stays above, its direct neighbours go below, and the rest alternate outwards from 本院
    if (m.tier === 'named') {
      const order = [...dots].sort((a, b) => parseFloat(a.x) - parseFloat(b.x))
      const at = Math.max(0, order.findIndex(d => d.l === '本院'))
      order.forEach((d, i) => { d.alt = Math.abs(i - at) % 2 === 1 })
    }
    return { name: m.name, tier: TIER[m.tier], v: m.ownValue, dots }
  }),
)

const ranking = computed(() => {
  const m = data.value.metrics.find(x => x.name === data.value.ranking.metric)
  if (!m?.values) return []
  const me = data.value.peers[own.value]
  return data.value.peers
    .map((n, i) => ({ n, v: m.values![i] ?? 0 }))
    .sort((a, b) => b.v - a.v)
    .map((x, i) => ({
      r: i + 1,
      n: x.n,
      v: x.v.toFixed(1),
      w: Math.max(0, Math.min(100, ((x.v - 90) / 10) * 100)).toFixed(0) + '%',
      me: x.n === me,
    }))
})

const COLS = 'grid gap-[18px] max-lg:grid-cols-[minmax(0,1fr)_auto_auto] max-lg:gap-x-3 max-lg:gap-y-1 lg:grid-cols-[200px_90px_minmax(0,1fr)_72px]'
</script>

<template>
  <PageSection label="B3 对标PK">
    <PageHeader title="对标 PK" :subtitle="data.subtitle" />
    <div class="-mt-2 flex flex-wrap gap-x-3.5 gap-y-1.5 text-xs text-ink-3" data-testid="b3-legend">
      <span v-for="t in LEGEND" :key="t" class="flex items-center gap-[5px] whitespace-nowrap">
        <span :class="cn('rounded px-[7px] py-px font-semibold', TIER[t].cls)">{{ TIER[t].label }}</span>{{ LEGEND_TXT[t] }}
      </span>
      <span class="text-ink-4">· 他院位置只反映同级分布,不对应任何机构</span>
    </div>

    <OwnDataEmpty
      v-if="empty"
      note="暂无本院对标数据"
      detail="本机构不在该同级对标组中,或本期尚未向本机构发布对标数据。"
    />

    <template v-else>
      <div class="yb-card px-6 pt-2 pb-card-y max-sm:px-4">
        <div :class="cn(COLS, 'border-b border-line-2 py-2.5 text-xs text-ink-4')">
          <span>指标</span><span class="max-lg:hidden">档位</span>
          <span class="flex justify-between max-lg:order-last max-lg:col-span-full"><span>← 较差</span><span>同级分布 · ● 本院</span><span>较好 →</span></span>
          <span class="text-right max-lg:hidden">本院</span>
        </div>
        <div v-for="r in rows" :key="r.name" :class="cn(COLS, 'items-center yb-tr border-b border-line-3 py-3.5')" :data-testid="'b3-row-' + r.name">
          <span class="font-medium">{{ r.name }}</span>
          <span :class="cn('justify-self-start rounded px-2 py-px text-[11px] font-semibold max-xl:text-[12px]', r.tier.cls)">{{ r.tier.label }}</span>
          <div class="relative max-lg:order-last max-lg:col-span-full max-xl:mb-1" :class="r.dots.some(d => d.alt) ? 'h-[48px]' : 'h-[34px]'">
            <div class="absolute inset-x-0 top-4 h-0.5 bg-line-2" />
            <div class="absolute top-3 left-1/4 h-2.5 w-1/2 rounded-[2px] bg-[#EEF3FC]" />
            <template v-for="(d, i) in r.dots" :key="i">
              <div
                class="absolute rounded-full border-2 border-white"
                :data-own="d.l === '本院' ? d.val : undefined"
                :style="{ left: d.x, top: d.t, width: d.s, height: d.s, marginLeft: d.m, background: d.c, boxShadow: d.sh, zIndex: d.z }"
              />
              <span
                v-if="d.show"
                class="absolute -translate-x-1/2 text-[10px] whitespace-nowrap max-xl:text-[12px]"
                :class="d.alt ? 'top-[30px]' : '-top-1.5'"
                :style="{ left: d.x, color: d.lc, fontWeight: d.lw }"
              >{{ d.l }}</span>
            </template>
          </div>
          <span class="yb-num text-right text-[17px] font-semibold text-brand">{{ r.v }}</span>
        </div>
      </div>

      <div v-if="ranking.length" class="yb-card px-6 py-card-y max-sm:px-4">
        <div class="mb-3 flex flex-wrap justify-between gap-x-4">
          <span class="text-[15px] font-semibold">{{ data.ranking.title }}</span>
          <span class="text-xs text-ink-4">{{ data.ranking.note }}</span>
        </div>
        <div
          v-for="h in ranking"
          :key="h.n"
          :class="cn('grid grid-cols-[20px_minmax(0,1fr)_48px] items-center gap-x-3 gap-y-1 py-1.5 text-[13px] sm:grid-cols-[28px_180px_1fr_56px]', h.me ? 'font-semibold' : 'font-normal')"
        >
          <span class="yb-num text-ink-5">{{ h.r }}</span>
          <span :class="cn('truncate', h.me ? 'text-brand' : 'text-ink-2')" :title="h.n">{{ h.n }}</span>
          <div class="h-2.5 rounded-[3px] bg-background max-sm:order-last max-sm:col-span-full">
            <div :class="cn('h-2.5 rounded-[3px]', h.me ? 'bg-brand' : 'bg-[#C9D3E1]')" :style="{ width: h.w }" />
          </div>
          <span class="yb-num text-right font-semibold">{{ h.v }}%</span>
        </div>
      </div>
    </template>
  </PageSection>
</template>
