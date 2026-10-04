<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fmtNum, portalAnalysisApi, type Offsite } from '@/api/portalAnalysis'
import BarTrack from '@/components/portal-analysis/BarTrack.vue'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import { pageDef } from '@/lib/nav'

/**
 * B7 区域外数据（机构门户）：本市参保人外出就医的汇总情况（省平台回流）。
 * 区域内 × 区域外象限：只按地区、等级、病种汇总；就医地机构明细不渲染（后端不存、不返回）。
 */
const page = pageDef('B7')!
const o = ref<Offsite | null>(null)
const error = ref('')

async function load() {
  try {
    o.value = await portalAnalysisApi.offsite()
    error.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}
onMounted(load)

const diseaseMax = computed(() => Math.max(...(o.value?.diseases.map((x) => x.fundSharePct) ?? [1])))
const regionMax = computed(() => Math.max(...(o.value?.regions.map((x) => x.fundSharePct) ?? [1])))
/** 省内蓝 / 省外橙 / 其他灰。 */
const regionColor = (scope: string) => (scope === '省内' ? 'var(--c-primary-solid)' : scope === '省外' ? 'var(--c-chart-amber-dk)' : 'var(--c-text-faint)')
const LEVEL = ['var(--c-primary-solid)', 'var(--c-chart-blue)', 'var(--c-text-faint)']
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />

    <div v-if="error" class="mt-5 rounded-[10px] border border-line bg-surface px-6 py-10 text-center text-[13px] text-ink-muted">
      加载失败:{{ error }} <button type="button" class="ml-2 cursor-pointer text-primary" @click="load">重试</button>
    </div>

    <template v-else-if="o">
      <!-- 固定来源横幅 -->
      <div class="mt-5 flex flex-wrap gap-x-4 gap-y-1 rounded-[10px] border border-primary-line bg-primary-tint px-[18px] py-2.5 text-[12px]" data-testid="source-banner">
        <b class="text-primary-ink">来源:{{ o.source }},截至 {{ o.asOf }}</b>
        <span class="text-ink-sub">{{ o.scopeNote }}</span>
      </div>

      <div class="mt-3.5 grid grid-cols-3 gap-3.5" data-testid="offsite-kpis">
        <KpiCard label="异地住院人次" :value="fmtNum(o.visits)" icon="bed" />
        <KpiCard label="异地就医基金支出" :value="String(o.fundYi)" unit="亿" icon="money" :desc="`占全市统筹基金支出 ${o.fundSharePct}%`" />
        <KpiCard
          v-if="o.capacity"
          label="与本院收治能力相关的外流"
          :value="fmtNum(o.capacity.visits)"
          unit="人次"
          icon="map"
          tone="warning"
          :desc="o.capacity.note"
        />
      </div>

      <div class="mt-3.5 grid grid-cols-2 items-start gap-3.5">
        <Panel title="外流病种 Top 5" sub="按基金支出">
          <div data-testid="diseases">
            <div v-for="x in o.diseases" :key="x.name" class="grid grid-cols-[150px_1fr_72px_48px] items-center gap-2.5 py-1.5 text-[12px]">
              <span class="text-ink">{{ x.name }}</span>
              <BarTrack :width="(x.fundSharePct / diseaseMax) * 100" color="var(--c-chart-amber-dk)" />
              <span class="text-right text-ink-sub">{{ fmtNum(x.visits) }} 人次</span>
              <span class="text-right text-ink">{{ x.fundSharePct.toFixed(1) }}%</span>
            </div>
          </div>
        </Panel>

        <Panel title="流向地区" sub="按基金支出占比">
          <template #head>
            <span class="flex items-center gap-1 text-[11px] text-ink-muted"><span class="size-2 rounded-[2px] bg-primary-solid" />省内</span>
            <span class="flex items-center gap-1 text-[11px] text-ink-muted"><span class="size-2 rounded-[2px] bg-[var(--c-chart-amber-dk)]" />省外</span>
          </template>
          <div data-testid="regions">
            <div v-for="x in o.regions" :key="x.name" class="grid grid-cols-[80px_40px_1fr_48px] items-center gap-2.5 py-1.5 text-[12px]" :data-scope="x.scope">
              <span class="text-ink">{{ x.name }}</span>
              <span class="text-ink-muted">{{ x.scope }}</span>
              <BarTrack :width="(x.fundSharePct / regionMax) * 100" :color="regionColor(x.scope)" />
              <span class="text-right text-ink">{{ x.fundSharePct.toFixed(1) }}%</span>
            </div>
          </div>
          <div class="mt-2.5 border-t border-divider pt-2.5" data-testid="levels">
            <div class="mb-1.5 text-[12px] text-ink-sub">
              就医地机构等级:<template v-for="(l, i) in o.levels" :key="l.level">{{ i ? ' · ' : '' }}{{ l.level }} {{ l.sharePct }}%</template>
            </div>
            <div class="flex h-2.5 overflow-hidden rounded-[3px]">
              <span v-for="(l, i) in o.levels" :key="l.level" :style="{ width: `${l.sharePct}%`, background: LEVEL[i % LEVEL.length] }" :title="`${l.level} ${l.sharePct}%`" />
            </div>
          </div>
        </Panel>
      </div>
      <div class="mt-3 text-[11px] text-ink-faint">就医地机构排名与明细仅医保局可见,机构门户不提供。</div>
    </template>
    <div v-else class="mt-5 h-[400px] animate-pulse rounded-[10px] border border-line bg-surface" />
  </div>
</template>
