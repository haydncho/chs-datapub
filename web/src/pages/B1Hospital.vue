<script setup lang="ts">
import { ref, watch } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { usePageData } from '@/api/client'
import { goPage } from '@/app/router'
import { B1_SEED } from '@/mock/B1'
import KpiCard from './B1/KpiCard.vue'
import BubblePanorama from './B1/BubblePanorama.vue'
import SettlementPanel from './B1/SettlementPanel.vue'
import DrgTable from './B1/DrgTable.vue'

const data = usePageData('B1', B1_SEED)

/** currently highlighted DRG — shared by bubble map, 偏离贡献 and the detail table */
const selected = ref(data.value.defaultDrg)
watch(() => data.value.drgs, drgs => {
  if (!drgs.some(d => d.code === selected.value)) selected.value = data.value.defaultDrg
})

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
    <PageHeader :title="data.hospital.name" :subtitle="data.hospital.subtitle">
      <button type="button"
        class="flex h-[34px] cursor-pointer items-center gap-1.5 rounded-lg max-xl:h-10 bg-brand-soft px-3 text-[12px] font-medium text-brand"
        @click="goPage('B4')"
      ><b class="yb-num text-[15px]">{{ data.todos.reportsToSign }}</b> 份报告待签收</button>
      <button type="button"
        class="flex h-[34px] cursor-pointer items-center gap-1.5 rounded-lg max-xl:h-10 bg-violet-soft px-3 text-[12px] font-medium text-violet"
        @click="goPage('B5')"
      ><b class="yb-num text-[15px]">{{ data.todos.verifyItems }}</b> 项核对 · 剩 {{ data.todos.verifyDaysLeft }} 天</button>
      <span class="flex h-[34px] items-center gap-1.5 rounded-lg max-xl:h-10 bg-warn-soft px-3 text-[12px] font-medium text-warn-ink">
        <b class="yb-num text-[15px]">{{ data.todos.watchItems }}</b> 项关注
      </span>
    </PageHeader>

    <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
      <KpiCard v-for="(k, i) in data.kpis" :key="k.name" :kpi="k" :icon="KPI_ICONS[i % KPI_ICONS.length]!" />
    </div>

    <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
      <BubblePanorama v-model:selected="selected" :data="data" />
      <SettlementPanel v-model:selected="selected" :data="data" />
    </div>

    <DrgTable v-model:selected="selected" :drgs="data.drgs" />
  </PageSection>
</template>
