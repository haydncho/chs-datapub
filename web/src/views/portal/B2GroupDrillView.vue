<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fmtNum, portalAnalysisApi, signed, type GroupDetail, type GroupIndex } from '@/api/portalAnalysis'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Chip from '@/components/shared/Chip.vue'
import Tag from '@/components/shared/Tag.vue'
import { pageDef } from '@/lib/nav'
import { notifyError } from '@/lib/notify'

/**
 * B2 病组下钻与专题（机构门户）：本院重点病组的费用结构、关键行为与标杆差距。
 * 标杆组以匿名方式呈现；小样本病组（病例 < 30）只显示「已并入其他」，后端不下发其数值。
 */
const page = pageDef('B2')!
const router = useRouter()
const route = useRoute()

const idx = ref<GroupIndex | null>(null)
const g = ref<GroupDetail | null>(null)
const sel = ref('')
const error = ref('')
const loading = ref(false)

async function pick(code: string) {
  sel.value = code
  loading.value = true
  try {
    const d = await portalAnalysisApi.group(code)
    if (sel.value === code) g.value = d
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

async function load() {
  try {
    idx.value = await portalAnalysisApi.groups()
    error.value = ''
    // 支持从 B1 本院全息图带病组编码进入（/b2?code=BR25）；不在本院重点病组里的编码回落到第一个
    const want = typeof route.query.code === 'string' ? route.query.code : ''
    const first = idx.value.groups.find((x) => x.code === want) ?? idx.value.groups[0]
    if (first) await pick(first.code)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}
onMounted(load)

/** 费用结构五类：主色 → 浅蓝 → 中性灰。 */
const SERIES = ['var(--c-primary-solid)', 'var(--c-chart-blue)', 'var(--c-s-blue-b)', 'var(--c-text-faint)', 'var(--c-bar-neutral-a)']
const gapText = (v: number, d: number, unit: string) => `${signed(v, d)} ${unit}`
const valText = (v: number, d: number, unit: string) => (unit === '%' ? `${fmtNum(v, d)}%` : `${fmtNum(v, d)} ${unit}`)
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />

    <div v-if="error" class="mt-5 rounded-[10px] border border-line bg-surface px-6 py-10 text-center text-[13px] text-ink-muted">
      加载失败:{{ error }} <button type="button" class="ml-2 cursor-pointer text-primary" @click="load">重试</button>
    </div>

    <template v-else-if="idx">
      <!-- 病组芯片 -->
      <div class="mt-5 flex flex-wrap items-center gap-2 rounded-[10px] border border-line bg-surface px-[18px] py-2.5" data-testid="group-chips">
        <span class="mr-1 text-[12px] text-ink-muted">本院重点病组</span>
        <Chip v-for="c in idx.groups" :key="c.code" :on="c.code === sel" :data-group="c.code" @click="pick(c.code)">
          <span class="font-mono">{{ c.code }}</span>
        </Chip>
        <div class="flex-1" />
        <span class="text-[12px] text-ink-muted">{{ idx.periodLabel }} · 同级组:{{ idx.peerGroup }}({{ idx.peerCount }} 家)</span>
      </div>

      <div class="mt-3.5 grid grid-cols-[minmax(0,1fr)_320px] items-start gap-3.5">
        <div v-if="g" class="flex flex-col gap-3.5" :class="loading ? 'opacity-60' : ''" data-testid="group-detail" :data-code="g.code">
          <!-- 病组 + KPI 4 宫格 -->
          <Panel>
            <div class="flex items-baseline gap-2">
              <span class="font-mono font-semibold text-primary">{{ g.code }}</span>
              <span class="text-[15px] font-semibold text-ink" data-testid="group-name">{{ g.name }}</span>
            </div>
            <div class="mt-3 grid grid-cols-4 gap-2.5" data-testid="group-kpis">
              <div class="rounded-lg bg-subtle px-3 py-2.5">
                <div class="text-[11px] text-ink-muted">本院病例数</div>
                <div class="text-[20px] font-semibold text-ink">{{ fmtNum(g.cases) }}</div>
              </div>
              <div class="rounded-lg bg-subtle px-3 py-2.5">
                <div class="text-[11px] text-ink-muted">本院例均费用</div>
                <div class="text-[20px] font-semibold text-ink">{{ fmtNum(g.avgCost) }} 元</div>
              </div>
              <div class="rounded-lg bg-subtle px-3 py-2.5">
                <div class="text-[11px] text-ink-muted">例均基金差额</div>
                <div class="text-[20px] font-semibold" :class="g.avgDiff > 0 ? 'text-danger' : 'text-success'" data-testid="kpi-diff">{{ signed(g.avgDiff) }} 元</div>
              </div>
              <div class="rounded-lg bg-subtle px-3 py-2.5">
                <div class="text-[11px] text-ink-muted">同级分位(次均费用)</div>
                <div class="text-[20px] font-semibold" :class="g.costPctConcern ? 'text-warning' : 'text-primary'" data-testid="kpi-pct">P{{ g.costPct }}</div>
              </div>
            </div>
          </Panel>

          <div class="grid grid-cols-2 gap-3.5">
            <!-- 费用结构 -->
            <Panel title="费用结构" sub="本院 vs 标杆组">
              <div class="grid grid-cols-[48px_1fr] items-center gap-2 text-[12px] text-ink-sub">
                <span>本院</span>
                <div class="flex h-3.5 overflow-hidden rounded-[3px]">
                  <span v-for="(m, i) in g.mix" :key="m.category" :style="{ width: `${m.own}%`, background: SERIES[i] }" :title="`${m.category} ${m.own}%`" />
                </div>
                <span>标杆组</span>
                <div class="flex h-3.5 overflow-hidden rounded-[3px]">
                  <span v-for="(m, i) in g.mix" :key="m.category" :style="{ width: `${m.bench}%`, background: SERIES[i] }" :title="`${m.category} ${m.bench}%`" />
                </div>
              </div>
              <div class="mt-2.5 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-ink-muted">
                <span v-for="(m, i) in g.mix" :key="m.category" class="flex items-center gap-1">
                  <span class="size-2 rounded-[2px]" :style="{ background: SERIES[i] }" />{{ m.category }} {{ m.own }}% / {{ m.bench }}%
                </span>
              </div>
            </Panel>

            <!-- 关键行为发生率 -->
            <Panel title="关键行为发生率" sub="本院 vs 同级中位">
              <div class="grid grid-cols-[1fr_64px_64px] border-b border-divider pb-1.5 text-[11px] text-ink-muted">
                <span>诊疗行为</span><span class="text-right">本院</span><span class="text-right">同级中位</span>
              </div>
              <div data-testid="behaviors">
                <div v-for="b in g.behaviors" :key="b.name" class="grid grid-cols-[1fr_64px_64px] border-b border-divider py-[7px] text-[12px]" :data-concern="b.concern">
                  <span class="text-ink">{{ b.name }}</span>
                  <span class="text-right font-medium" :class="b.concern ? 'text-warning' : 'text-ink'">{{ fmtNum(b.ownPct) }}%</span>
                  <span class="text-right text-ink-sub">{{ fmtNum(b.peerMedianPct) }}%</span>
                </div>
              </div>
              <div class="mt-2 text-[11px] text-ink-faint">橙色 = 朝不利方向偏离同级中位</div>
            </Panel>
          </div>

          <!-- 标杆差距 -->
          <Panel title="与标杆组的差距">
            <table class="data-table" data-testid="gaps">
              <thead><tr><th>指标</th><th class="text-right">本院</th><th class="text-right">标杆组(匿名)</th><th class="text-right">差距</th></tr></thead>
              <tbody>
                <tr v-for="x in g.gaps" :key="x.name">
                  <td class="text-ink">{{ x.name }}</td>
                  <td class="text-right text-ink">{{ valText(x.own, x.decimals, x.unit) }}</td>
                  <td class="text-right text-success">{{ valText(x.bench, x.decimals, x.unit) }}</td>
                  <td class="text-right font-medium" :class="x.worse ? 'text-danger' : 'text-success'">{{ gapText(x.gap, x.decimals, x.gapUnit) }}</td>
                </tr>
              </tbody>
            </table>
          </Panel>
        </div>
        <div v-else class="h-[460px] animate-pulse rounded-[10px] border border-line bg-surface" />

        <div class="flex flex-col gap-3.5">
          <!-- 小样本病组 -->
          <Panel title="小样本病组" data-testid="small-groups">
            <div class="-mt-1.5 mb-2 text-[12px] text-ink-muted">本院病例 &lt; 30 例,不单独展示</div>
            <div v-for="s in idx.small" :key="s.code" class="grid grid-cols-[44px_1fr_auto] items-center gap-2 border-b border-divider py-1.5 text-[12px] text-ink-muted">
              <span class="font-mono">{{ s.code }}</span>
              <span>{{ s.name }}</span>
              <Tag>已并入其他</Tag>
            </div>
            <div v-if="!idx.small.length" class="text-[12px] text-ink-faint">本期无小样本病组</div>
            <div class="mt-2 text-[12px] text-ink" data-testid="other-total">
              “其他”合计 {{ fmtNum(idx.other.cases) }} 例 · 例均差额 <span :class="idx.other.avgDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(idx.other.avgDiff) }} 元</span>
            </div>
          </Panel>

          <!-- 相关专题 -->
          <Panel title="相关专题">
            <div v-for="(t, i) in idx.topics" :key="t.title" class="py-2 text-[12px] text-ink" :class="i < idx.topics.length - 1 ? 'border-b border-divider' : ''">
              {{ t.title }}<template v-if="t.status"> · {{ t.status }}</template>
              <button type="button" class="ml-1 cursor-pointer text-primary hover:underline" :data-topic="t.page" @click="router.push(`/${t.page.toLowerCase()}`)">{{ t.linkLabel }} ›</button>
            </div>
          </Panel>
        </div>
      </div>
    </template>
    <div v-else class="mt-5 h-[460px] animate-pulse rounded-[10px] border border-line bg-surface" />
  </div>
</template>
