<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { HttpError } from '@/api/http'
import { regionalApi, type ProvinceSortKey, type ProvinceSummary, type SortDir } from '@/api/regional'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import SortTh from '@/components/regional/SortTh.vue'
import { Button } from '@/components/ui/button'
import { pageDef } from '@/lib/nav'

/**
 * C2 省级与区域外汇总：只看各统筹区发布情况与监测汇总层（接口不含机构级字段）。
 * 点击表头升 / 降序（排序口径与逾期判定由引擎计算）；逾期统筹区整行浅橙底、发布日期橙字。
 */
const page = pageDef('C2')!
const data = ref<ProvinceSummary | null>(null)
const error = ref('')
const sort = ref<ProvinceSortKey>('signPct')
const dir = ref<SortDir>('desc')
const busy = ref(false)

async function load() {
  busy.value = true
  error.value = ''
  try {
    data.value = await regionalApi.province(sort.value, dir.value)
  } catch (e) {
    error.value = e instanceof HttpError ? e.message : '加载失败,请稍后重试'
  } finally {
    busy.value = false
  }
}
onMounted(load)

function sortBy(k: ProvinceSortKey) {
  if (sort.value === k) dir.value = dir.value === 'desc' ? 'asc' : 'desc'
  else {
    sort.value = k
    dir.value = 'desc'
  }
  void load()
}

const cols: { key: ProvinceSortKey; label: string; num: boolean }[] = [
  { key: 'name', label: '统筹区', num: false },
  { key: 'period', label: '最近发布期次', num: false },
  { key: 'date', label: '发布日期', num: false },
  { key: 'signPct', label: '签收率', num: true },
  { key: 'readPct', label: '查阅率', num: true },
  { key: 'replyPct', label: '意见答复率', num: true },
  { key: 'balancePct', label: '基金当期结余率', num: true },
  { key: 'coveragePct', label: 'DRG/DIP 付费覆盖率', num: true },
  { key: 'avgDiff', label: '例均基金差额', num: true },
]

/** 百分比展示：负数用 −（U+2212），整数去掉 .0（签收 / 查阅 / 答复率按设计稿取整显示）。 */
const minus = (s: string) => s.replace(/^-/, '−')
const pct = (n: number) => minus(`${n.toFixed(1).replace(/\.0$/, '')}%`)
const pct1 = (n: number) => minus(`${n.toFixed(1)}%`)
const signed = (n: number) => `${n > 0 ? '+' : n < 0 ? '−' : ''}${Math.abs(n).toFixed(1)}`
const diffCls = (v: number) => (v > 0 ? 'text-danger' : v < 0 ? 'text-success' : 'text-ink')
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" title="省级与区域外汇总视图" />

    <div class="mt-5 rounded-[10px] border border-primary-line bg-primary-tint px-[18px] py-2.5 text-[12px] text-primary-ink" data-testid="c2-identity">
      当前身份:省级医保部门 · 只看汇总层。本视图不提供任何机构级数据。
    </div>

    <div v-if="error" class="mt-3.5 flex items-center gap-3 rounded-[10px] border border-danger-line bg-danger-soft px-[18px] py-3.5 text-[12px] text-danger-ink" data-testid="c2-error">
      {{ error }}
      <Button size="sm" variant="outline" class="ml-auto" @click="load">重试</Button>
    </div>

    <template v-if="data">
      <div class="mt-3.5 grid grid-cols-4 gap-3" data-testid="c2-kpis">
        <KpiCard label="本期按时发布" :value="String(data.kpis.onTime)" :unit="`/ ${data.kpis.total} 个统筹区`" icon="send" :tone="data.kpis.overdue.length ? 'warning' : 'success'" desc=" ">
          <template #desc>
            <span v-if="data.kpis.overdue.length" class="text-warning" data-testid="kpi-overdue">
              {{ data.kpis.overdue.map((o) => `${o.name} 逾期 ${o.days} 天`).join(';') }}
            </span>
            <span v-else>全部按时发布</span>
          </template>
        </KpiCard>
        <KpiCard label="平均签收率" :value="pct1(data.kpis.avgSignPct)" icon="check" :desc="`较上期 ${signed(data.kpis.signDeltaPt)} pt`" />
        <KpiCard label="平均意见答复率" :value="pct1(data.kpis.avgReplyPct)" icon="opinion" :desc="`低于 60% 的统筹区 ${data.kpis.lowReplyCount} 个`" />
        <KpiCard label="全省基金当期结余率" :value="pct1(data.kpis.balancePct)" icon="money" :tone="data.kpis.balancePct < 0 ? 'danger' : 'success'" desc=" ">
          <template #desc>
            <span :class="data.kpis.deficitCount ? 'text-danger' : ''">{{ data.kpis.deficitCount ? `${data.kpis.deficitCount} 个统筹区当期赤字` : '各统筹区均无当期赤字' }}</span>
          </template>
        </KpiCard>
      </div>

      <section class="mt-3.5">
        <div class="mb-2.5 flex items-center gap-3">
          <span class="sect-title">各统筹区发布与监测汇总</span>
          <span class="ml-auto text-[11px] text-ink-faint">点击表头排序 · 数据截至 {{ data.asOf }}</span>
        </div>
        <div class="table-scroll">
          <table class="data-table transition-opacity" :class="busy ? 'opacity-60' : ''" data-testid="c2-regions">
            <thead>
              <tr>
                <SortTh v-for="c in cols" :key="c.key" :align="c.num ? 'right' : 'left'" :active="sort === c.key" :dir="dir" :data-sort="c.key" @sort="sortBy(c.key)">
                  {{ c.label }}
                </SortTh>
              </tr>
            </thead>
            <tbody class="text-ink">
              <tr v-for="r in data.rows" :key="r.name" :data-region="r.name" :data-overdue="r.overdue || undefined" :class="r.overdue ? '[&>td]:!bg-warning-soft' : ''">
                <td :class="r.self ? 'font-semibold' : ''">{{ r.name }}</td>
                <td class="text-ink-sub">{{ r.lastPeriod }}</td>
                <td :class="r.overdue ? 'font-semibold text-warning' : 'text-ink-sub'">{{ r.dateLabel }}</td>
                <td class="text-right">{{ pct(r.signPct) }}</td>
                <td class="text-right" :class="r.readLow ? 'text-warning' : ''">{{ pct(r.readPct) }}</td>
                <td class="text-right">{{ pct(r.replyPct) }}</td>
                <td class="text-right" :class="r.deficit ? 'text-danger' : ''">{{ pct1(r.balancePct) }}</td>
                <td class="text-right">{{ pct1(r.coveragePct) }}</td>
                <td class="text-right" :class="diffCls(r.avgDiff)">{{ r.avgDiffText }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="mt-2 text-[11px] text-ink-faint">逾期:数据截止日晚于下一期应发布截止日仍未发布 · 查阅率低于 60% 标橙 · 结余率为负标红</div>
      </section>

      <div class="mt-3.5 grid grid-cols-3 gap-3" data-testid="c2-insights">
        <div v-for="i in data.insights" :key="i.title" class="rounded-[10px] border border-line bg-surface px-[18px] py-3.5 transition-colors hover:border-primary">
          <div class="mb-1 text-[13px] font-semibold text-ink">{{ i.title }}</div>
          <div class="text-[12px] leading-[1.7] text-ink-sub">{{ i.body }}</div>
        </div>
      </div>
    </template>
    <div v-else-if="!error" class="mt-10 text-center text-[12px] text-ink-faint">加载中…</div>
  </div>
</template>
