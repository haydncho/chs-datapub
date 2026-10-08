<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { usePageData } from '@/api/client'
import { goPage } from '@/app/router'
import { B1_EMPTY, B1_SEED } from '@/mock/B1'
import KpiCard from './B1/KpiCard.vue'
import BubblePanorama from './B1/BubblePanorama.vue'
import SettlementPanel from './B1/SettlementPanel.vue'
import DrgTable from './B1/DrgTable.vue'
import OwnDataEmpty from './B1/OwnDataEmpty.vue'
import { ownSeed, viewerOrgName } from './B1/ownSeed'
import { WATCH_BELOW } from './B1/percentile'

const data = usePageData('B1', ownSeed(B1_SEED, B1_EMPTY))
const empty = computed(() => !!data.value.noOwnData || data.value.drgs.length === 0)

/** currently highlighted DRG — shared by bubble map, 偏离贡献 and the detail table */
const selected = ref(data.value.defaultDrg)
watch(() => data.value.drgs, drgs => {
  if (!drgs.some(d => d.code === selected.value)) selected.value = data.value.defaultDrg
})

/** 关注 = indicators whose 同级分位 is below P35 (ranked by performance, so low = worse than most peers) */
const watchKpis = computed(() => data.value.kpis.filter(k => k.percentile < WATCH_BELOW))
const watchTitle = computed(() =>
  watchKpis.value.length
    ? '同级分位低于 P' + WATCH_BELOW + ':' + watchKpis.value.map(k => `${k.name} P${k.percentile}`).join('、') + ' · 点击查看对标PK'
    : '暂无同级分位低于 P' + WATCH_BELOW + ' 的指标',
)

/** KPI icons, in KPI order (CMI, 次均费用, 费用消耗指数, 时间消耗指数, 医保外费用占比, 结算清单质控率) */
const KPI_ICONS = [
  'M12 3l9 5-9 5-9-5 9-5zM3 13l9 5 9-5',
  'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9 10h6M9 14h6M12 10v7',
  'M3 17l6-6 4 4 8-8M15 7h6v6',
  'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  'M4 20V11M10 20V5M16 20v-6M21 20H3',
  'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3zM8.5 12l2.5 2.5 4.5-5',
]
</script>

<template>
  <PageSection label="B1 本院全景">
    <PageHeader :title="data.hospital.name || viewerOrgName()" :subtitle="data.hospital.subtitle">
      <!-- wrap only between「·」segments so a narrow header never leaves a single orphaned character -->
      <template v-if="data.hospital.subtitle" #subtitle>
        <template v-for="(seg, i) in data.hospital.subtitle.split(' · ')" :key="i"><template v-if="i"> · </template><span class="whitespace-nowrap">{{ seg }}</span></template>
      </template>
      <template v-if="!empty" #default>
        <button type="button"
          class="flex h-[34px] cursor-pointer items-center gap-1.5 rounded-lg max-xl:h-10 bg-brand-soft px-3 text-[12px] font-medium text-brand"
          @click="goPage('B4')"
        ><b class="yb-num text-[15px]">{{ data.todos.reportsToSign }}</b> 份报告待签收</button>
        <button type="button"
          class="flex h-[34px] cursor-pointer items-center gap-1.5 rounded-lg max-xl:h-10 bg-violet-soft px-3 text-[12px] font-medium text-violet"
          @click="goPage('B5')"
        ><b class="yb-num text-[15px]">{{ data.todos.verifyItems }}</b> 项核对 · 剩 {{ data.todos.verifyDaysLeft }} 天</button>
        <button type="button"
          data-testid="b1-watch"
          class="flex h-[34px] cursor-pointer items-center gap-1.5 rounded-lg max-xl:h-10 bg-warn-soft px-3 text-[12px] font-medium text-warn-ink"
          :title="watchTitle"
          :aria-label="watchKpis.length + ' 项关注:' + watchTitle"
          @click="goPage('B3')"
        ><b class="yb-num text-[15px]">{{ watchKpis.length }}</b> 项关注 →</button>
      </template>
    </PageHeader>

    <OwnDataEmpty v-if="empty" :note="data.noOwnDataNote" />

    <template v-else>
      <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <KpiCard v-for="(k, i) in data.kpis" :key="k.name" :kpi="k" :icon="KPI_ICONS[i % KPI_ICONS.length]!" />
      </div>
      <p class="-mt-1 text-[12px] text-ink-4" data-testid="b1-percentile-note">
        同级分位 = 本院在同级组(市三级 6 家)中按<b class="font-medium text-ink-2">表现好坏</b>的排位,<b class="font-medium text-ink-2">越高越好</b>:P100 为同级最优;费用、指数类指标数值越低排位越高。低于 P{{ WATCH_BELOW }} 计为「关注」。
      </p>

      <div class="grid grid-cols-1 items-start gap-4 lg:grid-cols-[minmax(0,1fr)_340px] xl:grid-cols-[minmax(0,1fr)_380px]">
        <BubblePanorama v-model:selected="selected" :data="data" />
        <SettlementPanel v-model:selected="selected" :data="data" />
      </div>

      <DrgTable v-model:selected="selected" :drgs="data.drgs" :total-cases="data.totalCases" :settlement="data.settlement" />
    </template>
  </PageSection>
</template>
