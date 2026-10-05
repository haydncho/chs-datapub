<script setup lang="ts">
import { computed } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { cn } from '@/lib/utils'
import { BRAND, BRAND_SOFT, INK } from '@/lib/palette'
import { usePageData } from '@/api/client'
import { B3_SEED, type B3Tier } from '@/mock/B3'

const data = usePageData('B3', B3_SEED)

const TIER: Record<B3Tier, { label: string; cls: string }> = {
  pct: { label: '匿名分位', cls: 'bg-brand-soft text-brand' },
  anon: { label: '匿名编号', cls: 'bg-violet-soft text-violet' },
  named: { label: '具名', cls: 'bg-ok-soft text-ok-ink' },
}
const LEGEND: B3Tier[] = ['pct', 'anon', 'named']
const LEGEND_TXT: Record<B3Tier, string> = { pct: '仅见本院位置', anon: '他院以字母代替', named: '显示机构名称' }

const own = computed(() => data.value.ownIndex)
const shortName = (n: string) => n.replace('示例市', '')

const rows = computed(() =>
  data.value.metrics.map(m => {
    const mn = Math.min(...m.values)
    const mx = Math.max(...m.values)
    const dots = m.values.map((x, i) => {
      let pos = (x - mn) / (mx - mn || 1)
      if (!m.higherIsBetter) pos = 1 - pos
      const me = i === own.value
      const show = m.tier !== 'pct' || me
      return {
        x: (4 + pos * 92).toFixed(1) + '%',
        s: me ? '16px' : '12px',
        m: me ? '-8px' : '-6px',
        t: me ? '9px' : '11px',
        c: me ? BRAND : show ? '#9DB0CC' : '#DCE1E9',
        sh: me ? `0 0 0 3px ${BRAND_SOFT}` : 'none',
        z: me ? 5 : 1,
        show,
        alt: !me && m.tier === 'named' && i % 2 === 1,
        l: me ? '本院' : m.tier === 'named' ? shortName(data.value.peers[i] ?? '') : 'ABCDEF'[i < own.value ? i : i - 1],
        lc: me ? BRAND : INK[3],
        lw: me ? 700 : 400,
      }
    })
    return { name: m.name, tier: TIER[m.tier], v: m.ownValue, dots }
  }),
)

const ranking = computed(() => {
  const m = data.value.metrics.find(x => x.name === data.value.ranking.metric)
  if (!m) return []
  const me = data.value.peers[own.value]
  return data.value.peers
    .map((n, i) => ({ n, v: m.values[i] ?? 0 }))
    .sort((a, b) => b.v - a.v)
    .map((x, i) => ({
      r: i + 1,
      n: x.n,
      v: x.v.toFixed(1),
      w: (((x.v - 90) / 10) * 100).toFixed(0) + '%',
      me: x.n === me,
    }))
})

const COLS = 'grid gap-[18px] max-lg:grid-cols-[minmax(0,1fr)_auto_auto] max-lg:gap-x-3 max-lg:gap-y-1 lg:grid-cols-[200px_90px_minmax(0,1fr)_72px]'
</script>

<template>
  <PageSection label="B3 对标PK">
    <PageHeader title="对标 PK" :subtitle="data.subtitle">
      <div class="flex flex-wrap gap-x-3.5 gap-y-1.5 text-xs text-ink-3">
        <span v-for="t in LEGEND" :key="t" class="flex items-center gap-[5px] whitespace-nowrap">
          <span :class="cn('rounded px-[7px] py-px font-semibold', TIER[t].cls)">{{ TIER[t].label }}</span>{{ LEGEND_TXT[t] }}
        </span>
      </div>
    </PageHeader>

    <div class="yb-card px-6 pt-2 pb-card-y">
      <div :class="cn(COLS, 'border-b border-line-2 py-2.5 text-xs text-ink-4')">
        <span>指标</span><span class="max-lg:hidden">档位</span>
        <span class="flex justify-between max-lg:order-last max-lg:col-span-full"><span>← 较差</span><span>同级分布 · ● 本院</span><span>较好 →</span></span>
        <span class="text-right max-lg:hidden">本院</span>
      </div>
      <div v-for="r in rows" :key="r.name" :class="cn(COLS, 'items-center border-b border-line-3 py-3.5')">
        <span class="font-medium">{{ r.name }}</span>
        <span :class="cn('justify-self-start rounded px-2 py-px text-[11px] font-semibold', r.tier.cls)">{{ r.tier.label }}</span>
        <div class="relative h-[34px] max-lg:order-last max-lg:col-span-full max-xl:mb-1" :class="r.dots.some(d => d.alt) && 'max-xl:h-[48px]'">
          <div class="absolute inset-x-0 top-4 h-0.5 bg-line-2" />
          <div class="absolute top-3 left-1/4 h-2.5 w-1/2 rounded-[2px] bg-[#EEF3FC]" />
          <template v-for="(d, i) in r.dots" :key="i">
            <div
              class="absolute rounded-full border-2 border-white"
              :style="{ left: d.x, top: d.t, width: d.s, height: d.s, marginLeft: d.m, background: d.c, boxShadow: d.sh, zIndex: d.z }"
            />
            <span
              v-if="d.show"
              class="absolute -translate-x-1/2 text-[10px] whitespace-nowrap"
              :class="d.alt ? 'max-xl:top-[30px] xl:-top-1.5' : '-top-1.5'"
              :style="{ left: d.x, color: d.lc, fontWeight: d.lw }"
            >{{ d.l }}</span>
          </template>
        </div>
        <span class="yb-num text-right text-[17px] font-semibold text-brand">{{ r.v }}</span>
      </div>
    </div>

    <div class="yb-card px-6 py-card-y">
      <div class="mb-3 flex justify-between">
        <span class="text-[15px] font-semibold">{{ data.ranking.title }}</span>
        <span class="text-xs text-ink-4">{{ data.ranking.note }}</span>
      </div>
      <div
        v-for="h in ranking"
        :key="h.n"
        :class="cn('grid grid-cols-[28px_180px_1fr_56px] items-center gap-3 py-1.5 text-[13px]', h.me ? 'font-semibold' : 'font-normal')"
      >
        <span class="yb-num text-ink-5">{{ h.r }}</span>
        <span :class="h.me ? 'text-brand' : 'text-ink-2'">{{ h.n }}</span>
        <div class="h-2.5 rounded-[3px] bg-background">
          <div :class="cn('h-2.5 rounded-[3px]', h.me ? 'bg-brand' : 'bg-[#C9D3E1]')" :style="{ width: h.w }" />
        </div>
        <span class="yb-num text-right font-semibold">{{ h.v }}%</span>
      </div>
    </div>
  </PageSection>
</template>
