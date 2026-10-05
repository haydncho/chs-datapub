<script setup lang="ts">
import { computed } from 'vue'
import { PageHeader, PageSection, StatCard } from '@/components/yb'
import { usePageData } from '@/api/client'
import { fmt } from '@/lib/format'
import { A, BRAND, G, INK } from '@/lib/palette'
import { B7_SEED, type B7Icon, type B7Source } from '@/mock/B7'

const data = usePageData('B7', B7_SEED)

const ICON: Record<B7Icon, string> = {
  users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21c0-3.9 3.1-7 7-7s7 3.1 7 7M16 3.5a4 4 0 0 1 0 7.5M22 21c0-3.2-2-5.8-5-6.7',
  coin: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9 10h6M9 14h6M12 10v7',
  map: 'M9 4L3 6v14l6-2 6 2 6-2V4l-6 2-6-2zM9 4v14M15 6v14',
  trend: 'M3 17l6-6 4 4 8-8M15 7h6v6',
}
const SRC_COLOR: Record<B7Source['group'], string> = { county: G, city: BRAND, province: A, other: INK[4] }

const sources = computed(() => {
  const s = data.value.sources
  const total = s.reduce((a, x) => a + x.count, 0)
  const max = Math.max(...s.map(x => x.count))
  return s.map(x => ({ ...x, w: (x.count / max) * 100 + '%', pct: ((x.count / total) * 100).toFixed(1) + '%', color: SRC_COLOR[x.group] }))
})

const COLS = 'grid grid-cols-[52px_minmax(0,1fr)_64px_90px_90px] gap-2.5'
</script>

<template>
  <PageSection label="B7 区域外患者">
    <PageHeader title="区域外患者 · 本院收治" :subtitle="data.subtitle" />

    <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <StatCard
        v-for="k in data.kpis"
        :key="k.label"
        :label="k.label"
        :value="k.value"
        :sub="k.sub"
        sub-color="var(--ink-4)"
        size="sm"
        :tone="k.tone"
        :icon="ICON[k.icon]"
      />
    </div>

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
      <div class="yb-card px-card-x py-card-y">
        <div class="mb-3 text-[15px] font-semibold">来源地</div>
        <div v-for="r in sources" :key="r.name" class="grid grid-cols-[90px_1fr_56px_70px] items-center gap-2.5 py-[9px] text-[13px]">
          <span>{{ r.name }}</span>
          <div class="h-3 rounded-[3px] bg-background">
            <div class="h-3 rounded-[3px]" :style="{ width: r.w, background: r.color }" />
          </div>
          <span class="yb-num text-right font-semibold">{{ r.count }}</span>
          <span class="text-right text-xs text-ink-4">{{ r.pct }}</span>
        </div>
      </div>

      <div class="yb-card px-card-x py-card-y">
        <div class="mb-3 text-[15px] font-semibold">主要病组 · 与本地患者对比</div>
        <div :class="[COLS, 'pb-1.5 text-[11px] text-ink-5']">
          <span>编码</span><span>病组</span><span class="text-right">人次</span><span class="text-right">次均 · 异地</span><span class="text-right">次均 · 本地</span>
        </div>
        <div v-for="r in data.drgs" :key="r.code" :class="[COLS, 'items-center border-t border-line-3 py-[7px] text-[13px]']">
          <span class="yb-num font-semibold text-brand">{{ r.code }}</span>
          <span>{{ r.name }}</span>
          <span class="yb-num text-right">{{ r.cases }}</span>
          <span :class="['yb-num text-right font-semibold', r.offsiteAvg > r.localAvg * 1.05 ? 'text-bad' : 'text-ink-1']">{{ fmt(r.offsiteAvg) }}</span>
          <span class="yb-num text-right text-ink-4">{{ fmt(r.localAvg) }}</span>
        </div>
      </div>
    </div>
  </PageSection>
</template>
