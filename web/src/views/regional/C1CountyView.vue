<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { HttpError } from '@/api/http'
import type { Tone } from '@/api/types'
import { regionalApi, type CountyInstitution, type CountyOverview, type MonitorStatus } from '@/api/regional'
import PageHeader from '@/components/shared/PageHeader.vue'
import Tag from '@/components/shared/Tag.vue'
import SortTh from '@/components/regional/SortTh.vue'
import { Button } from '@/components/ui/button'
import { pageDef } from '@/lib/nav'

/**
 * C1 县区医保视图：本县区机构具名（数值列可排序）；其他县区仅汇总 + 排名（本县高亮）；医共体 14 项监测指标。
 * 机构明细只含本县区（服务端按会话机构裁剪）；排名与指标判级来自引擎。
 */
const page = pageDef('C1')!
const data = ref<CountyOverview | null>(null)
const error = ref('')

async function load() {
  error.value = ''
  try {
    data.value = await regionalApi.county()
  } catch (e) {
    error.value = e instanceof HttpError ? e.message : '加载失败,请稍后重试'
  }
}
onMounted(load)

type NumKey = 'cases' | 'avgDiff' | 'listQcPct' | 'costPctl'
const cols: { key: NumKey; label: string }[] = [
  { key: 'cases', label: '病例数' },
  { key: 'avgDiff', label: '例均基金差额' },
  { key: 'listQcPct', label: '清单质控率' },
  { key: 'costPctl', label: '次均费用分位' },
]
const sortKey = ref<NumKey>('cases')
const sortDir = ref<'asc' | 'desc'>('desc')
function sortBy(k: NumKey) {
  if (sortKey.value === k) sortDir.value = sortDir.value === 'desc' ? 'asc' : 'desc'
  else {
    sortKey.value = k
    sortDir.value = 'desc'
  }
}
const rows = computed<CountyInstitution[]>(() => {
  const list = [...(data.value?.institutions ?? [])]
  const s = sortDir.value === 'desc' ? -1 : 1
  return list.sort((a, b) => (a[sortKey.value] - b[sortKey.value]) * s || a.name.localeCompare(b.name, 'zh'))
})

const fmt = (n: number) => Math.round(n).toLocaleString('zh-CN')
const diffCls = (v: number) => (v > 0 ? 'text-danger' : v < 0 ? 'text-success' : 'text-ink')

const ST: Record<MonitorStatus, [string, Tone]> = { ok: ['正常', 'success'], warn: ['关注', 'warning'], bad: ['预警', 'danger'] }
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />

    <div v-if="error" class="mt-5 flex items-center gap-3 rounded-[10px] border border-danger-line bg-danger-soft px-[18px] py-3.5 text-[12px] text-danger-ink" data-testid="c1-error">
      {{ error }}
      <Button size="sm" variant="outline" class="ml-auto" @click="load">重试</Button>
    </div>

    <template v-else-if="data">
      <div class="mt-5 grid grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)] items-start gap-3.5">
        <!-- 本县区机构（具名） -->
        <section>
          <div class="mb-2.5 flex items-center gap-3">
            <span class="sect-title">{{ data.county }}定点机构(具名)</span>
            <span class="ml-auto text-[11px] text-ink-faint">{{ data.period }} · 点击数值列排序</span>
          </div>
          <div class="table-scroll">
            <table class="data-table" data-testid="c1-institutions">
              <thead>
                <tr>
                  <th>机构</th>
                  <th>等级</th>
                  <SortTh v-for="c in cols" :key="c.key" align="right" :active="sortKey === c.key" :dir="sortDir" :data-sort="c.key" @sort="sortBy(c.key)">
                    {{ c.label }}
                  </SortTh>
                </tr>
              </thead>
              <tbody class="text-ink-sub">
                <tr v-for="r in rows" :key="r.name" :data-inst="r.name">
                  <td class="font-medium text-ink">{{ r.name }}</td>
                  <td>{{ r.level }}</td>
                  <td class="text-right">{{ fmt(r.cases) }}</td>
                  <td class="text-right" :class="diffCls(r.avgDiff)">{{ r.avgDiffText }}</td>
                  <td class="text-right" :class="r.qcLow ? 'font-medium text-warning' : 'text-ink'" :title="r.qcLow ? `低于 ${data.qcThreshold}%` : undefined">{{ r.listQcPct.toFixed(1) }}%</td>
                  <td class="text-right">P{{ r.costPctl }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="mt-2 text-[11px] text-ink-faint">例均基金差额:红 = 逆差、绿 = 结余;清单质控率低于 {{ data.qcThreshold }}% 标橙</div>
        </section>

        <!-- 各县区汇总与排名 -->
        <section>
          <div class="mb-2.5 flex items-center gap-3">
            <span class="sect-title">各县区汇总与排名</span>
            <span class="ml-auto text-[11px] text-ink-faint">其他县区仅显示汇总,不含机构明细</span>
          </div>
          <table class="data-table" data-testid="c1-counties">
            <thead><tr><th>县区</th><th>排名</th><th class="text-right">例均差额</th><th class="text-right">质控率</th></tr></thead>
            <tbody class="text-ink-sub">
              <tr v-for="c in data.counties" :key="c.name" :data-on="c.self" :data-county="c.name" :data-self="c.self || undefined">
                <td :class="c.self ? 'font-semibold text-primary' : 'text-ink'">
                  {{ c.name }}<Tag v-if="c.self" tone="primary" class="ml-1.5">本县区</Tag>
                </td>
                <td :class="c.self ? 'font-semibold text-ink' : ''" :title="`${data.rankBasis} ${c.score}`">第 {{ c.rank }}</td>
                <td class="text-right" :class="[diffCls(c.avgDiff), c.self ? 'font-semibold' : '']">{{ c.avgDiffText }}</td>
                <td class="text-right" :class="c.self ? 'font-semibold text-ink' : ''">{{ c.listQcPct.toFixed(1) }}%</td>
              </tr>
            </tbody>
          </table>
          <div class="mt-2 text-[11px] text-ink-faint">排名口径:{{ data.rankBasis }}(由分析引擎计算)</div>
        </section>
      </div>

      <!-- 医共体 14 项监测指标 -->
      <section class="mt-3.5 rounded-[10px] border border-line bg-surface px-[18px] py-4" data-testid="c1-monitors">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <span class="sect-title">{{ data.county }}医共体 · {{ data.indicators.length }} 项医保监测指标</span>
          <span class="flex items-center gap-1.5">
            <Tag tone="success">正常 {{ data.statusCounts.ok }}</Tag>
            <Tag tone="warning">关注 {{ data.statusCounts.warn }}</Tag>
            <Tag tone="danger">预警 {{ data.statusCounts.bad }}</Tag>
          </span>
          <span class="ml-auto text-[11px] text-ink-faint">{{ data.alliancePeriod }}</span>
        </div>
        <div class="grid grid-cols-7 gap-2">
          <div
            v-for="m in data.indicators"
            :key="m.name"
            class="rounded-lg border border-line px-2.5 py-2.5 transition-colors hover:border-primary"
            :title="m.rule"
            :data-monitor="m.name"
            :data-status="m.status"
          >
            <div class="min-h-[34px] text-[11px] leading-[1.5] text-ink-muted">{{ m.name }}</div>
            <div class="text-[16px] font-semibold text-ink">{{ m.display }}</div>
            <Tag :tone="ST[m.status][1]" class="mt-1">{{ ST[m.status][0] }}</Tag>
          </div>
        </div>
        <div class="mt-2.5 text-[11px] text-ink-faint">按关注线 / 预警线判级(悬停查看阈值)</div>
      </section>
    </template>
    <div v-else class="mt-10 text-center text-[12px] text-ink-faint">加载中…</div>
  </div>
</template>
