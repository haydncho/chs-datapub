<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { topicApi } from '@/api'
import type { Topic } from '@/api/types'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'
import { useAuthStore } from '@/stores/auth'

/**
 * A7 病组专题工作台（流程 2）：七段式结构；文稿区为引擎初稿，逐段人工审定，七段全审定才能提交机构核对与专家组审核。
 * 受控分析环境：只输出经审核的聚合结果。专家组列席身份只读。
 */
const page = pageDef('A7')!
const auth = useAuthStore()
const CODE = 'BR25'

const t = ref<Topic | null>(null)
const error = ref('')
const sec = ref(1)
const busy = ref(false)

async function load() {
  try {
    t.value = await topicApi.get(CODE)
    error.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}
onMounted(load)

const readOnly = computed(() => auth.user?.role === 'EXPERT')
const canSubmit = computed(() => ['CONVENER', 'ADMIN_GROUP'].includes(auth.user?.role ?? ''))
const cur = computed(() => t.value?.sections.find((s) => s.idx === sec.value))
const n = (v: number) => v.toLocaleString('zh-CN')
const signed = (v: number) => (v >= 0 ? '+' : '−') + n(Math.abs(v))

async function act(kind: 'approve' | 'regenerate') {
  if (!t.value) return
  busy.value = true
  try {
    const s = kind === 'approve' ? await topicApi.approve(CODE, sec.value) : await topicApi.regenerate(CODE, sec.value)
    t.value.sections = t.value.sections.map((x) => (x.idx === s.idx ? s : x))
    t.value.approvedCount = t.value.sections.filter((x) => x.approved).length
    notify(kind === 'approve' ? `第 ${sec.value} 段已审定` : `已按最新数据重新生成初稿(第 ${s.draftVersion} 版,保留历史版本)`)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

async function submit() {
  if (!t.value) return
  const left = 7 - t.value.approvedCount
  if (left > 0) return notify(`还有 ${left} 段未审定,不能提交`)
  busy.value = true
  try {
    notify((await topicApi.submit(CODE)).message)
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

const SERIES = ['var(--c-primary-solid)', 'var(--c-chart-blue)', 'var(--c-s-blue-b)', 'var(--c-bar-neutral-b)', 'var(--c-bar-light-b)']
const levelColor = ['var(--c-primary-solid)', 'var(--c-chart-blue)', 'var(--c-s-blue-b)', 'var(--c-bar-light-b)']
const wfColor = (i: number) => ['var(--c-bar-neutral-b)', 'var(--c-s-blue-b)', 'var(--c-chart-amber-dk)', 'var(--c-red-solid)'][i]
const pct = (v: number) => (t.value ? (v / t.value.waterfall.axisMax) * 100 : 0)
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <div v-if="t" class="mt-5 grid grid-cols-4 gap-3.5" data-testid="a7-kpis">
      <KpiCard label="专题病例" :value="t.overview.cases.toLocaleString('zh-CN')" unit="例" icon="bed" tone="primary" :desc="`${t.overview.orgs} 家机构收治`" />
      <KpiCard label="例均基金差额" :value="`${t.overview.avgDiff > 0 ? '+' : ''}${t.overview.avgDiff.toLocaleString('zh-CN')}元`" icon="money" :tone="t.overview.avgDiff > 0 ? 'danger' : 'success'" small :desc="`差额合计 ${t.overview.diffTotalWan} 万`" />
      <KpiCard label="次均总费用" :value="`${t.overview.avgCost.toLocaleString('zh-CN')}元`" icon="pct" tone="warning" small :delta="{ text: `${Math.abs(t.overview.yoyPct)}%`, dir: t.overview.yoyPct >= 0 ? 'up' : 'down', good: false }" desc="同比" />
      <KpiCard label="偏离标杆机构" :value="String(t.overview.deviantOrgs)" unit="家" icon="alert" tone="ai" :desc="`标杆机构 ${t.overview.benchOrgs} 家`" />
    </div>

    <div v-if="error" class="mt-5 rounded-2xl border border-line-soft bg-surface shadow-card px-6 py-10 text-center text-[13px] text-ink-muted">
      专题加载失败:{{ error }} <button type="button" class="ml-2 cursor-pointer text-primary" @click="load">重试</button>
    </div>

    <template v-else-if="t">
      <!-- 专题条 -->
      <div class="mt-5 flex flex-wrap items-center gap-3 rounded-2xl border border-line-soft bg-surface shadow-card px-[18px] py-3">
        <span class="font-mono font-semibold text-primary">{{ t.code }}</span>
        <span class="text-[15px] font-semibold text-ink">{{ t.name }} · {{ t.period }}专题</span>
        <Tag tone="success">来自{{ t.source }}</Tag>
        <span class="text-[11px] text-ink-muted">受控分析环境 · 仅可导出经审核的聚合结果</span>
        <div class="flex-1" />
        <span class="text-[12px] text-ink-sub" data-testid="approved-count">已审定 {{ t.approvedCount }}/7 段</span>
        <Tag v-if="t.submitted" tone="primary">已提交审核</Tag>
        <Button v-else-if="canSubmit" size="sm" :disabled="busy" data-testid="submit-topic" @click="submit">提交机构核对与专家组审核</Button>
      </div>

      <div class="mt-3.5 grid grid-cols-[200px_minmax(0,1fr)_320px] items-start gap-3.5">
        <!-- 七段导航 -->
        <nav class="rounded-2xl border border-line-soft bg-surface shadow-card p-2" data-testid="sections">
          <button
            v-for="s in t.sections"
            :key="s.idx"
            type="button"
            class="flex w-full cursor-pointer items-center gap-2 rounded-lg px-2.5 py-2 text-left text-[13px]"
            :class="s.idx === sec ? 'bg-primary-tint font-semibold text-primary' : 'text-ink hover:bg-hover'"
            @click="sec = s.idx"
          >
            <span class="w-4 text-[11px] font-normal text-ink-faint">{{ s.idx }}</span>
            <span class="flex-1">{{ s.name }}</span>
            <span v-if="s.approved" class="text-[12px] text-success">✓</span>
          </button>
        </nav>

        <!-- 内容区 -->
        <Panel :title="`${sec}. ${cur?.name ?? ''}`" class="min-h-[460px]">
          <template v-if="sec === 1">
            <div class="grid grid-cols-4 gap-2.5">
              <div class="rounded-lg bg-subtle px-3 py-2.5"><div class="text-[11px] text-ink-muted">病例数</div><div class="text-[20px] font-semibold">{{ n(t.overview.cases) }}</div><div class="text-[11px] text-ink-muted">{{ t.overview.orgs }} 家机构收治</div></div>
              <div class="rounded-lg bg-subtle px-3 py-2.5"><div class="text-[11px] text-ink-muted">次均总费用</div><div class="text-[20px] font-semibold">{{ n(t.overview.avgCost) }} 元</div><div class="text-[11px]" :class="t.overview.yoyPct > 0 ? 'text-danger' : 'text-success'">同比 {{ t.overview.yoyPct > 0 ? '+' : '' }}{{ t.overview.yoyPct }}%</div></div>
              <div class="rounded-lg bg-subtle px-3 py-2.5"><div class="text-[11px] text-ink-muted">例均基金差额</div><div class="text-[20px] font-semibold" :class="t.overview.avgDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(t.overview.avgDiff) }} 元</div><div class="text-[11px] text-ink-muted">逆差合计 {{ t.overview.diffTotalWan }} 万</div></div>
              <div class="rounded-lg bg-subtle px-3 py-2.5"><div class="text-[11px] text-ink-muted">平均住院日</div><div class="text-[20px] font-semibold">{{ t.overview.los }} 天</div><div class="text-[11px] text-ink-muted">同级中位 {{ t.overview.peerLos }} 天</div></div>
            </div>
            <div class="mt-4 mb-1.5 text-[12px] text-ink-sub">按机构等级分布(病例数)</div>
            <div class="flex h-[22px] overflow-hidden rounded text-[11px] text-white">
              <span v-for="(l, i) in t.overview.levels" :key="l.name" class="flex items-center overflow-hidden pl-2 whitespace-nowrap" :style="{ width: `${l.pct}%`, background: levelColor[i] }" :class="i >= 2 ? 'text-ink' : ''">
                {{ l.pct >= 10 ? `${l.name} ${l.pct}%` : '' }}
              </span>
            </div>
          </template>

          <template v-else-if="sec === 2">
            <div class="grid grid-cols-[70px_1fr] items-center gap-x-2.5 gap-y-2 text-[12px]">
              <template v-for="r in t.costMix.rows" :key="r.name">
                <span class="text-ink">{{ r.name }}</span>
                <div class="flex h-4 overflow-hidden rounded-[3px]">
                  <span v-for="(v, i) in r.values" :key="i" :style="{ width: `${v}%`, background: SERIES[i] }" :title="`${t.costMix.categories[i]} ${v}%`" />
                </div>
              </template>
            </div>
            <div class="mt-2.5 flex gap-3 text-[11px] text-ink-muted">
              <span v-for="(c, i) in t.costMix.categories" :key="c"><span :style="{ color: SERIES[i] }">■</span> {{ c }}</span>
            </div>
          </template>

          <template v-else-if="sec === 3">
            <table class="data-table">
              <thead><tr><th>行为变量</th><th class="text-right">发生率</th><th class="text-right">有该行为次均</th><th class="text-right">无该行为次均</th><th class="text-right">费用倍率</th></tr></thead>
              <tbody class="text-ink-sub">
                <tr v-for="b in t.behaviors.rows" :key="b.name">
                  <td class="text-ink">{{ b.name }}</td>
                  <td class="text-right">{{ b.ratePct }}%</td>
                  <td class="text-right">{{ n(b.withAvg) }}</td>
                  <td class="text-right">{{ n(b.withoutAvg) }}</td>
                  <td class="text-right font-semibold" :class="b.level === 'high' ? 'text-danger' : 'text-warning'">×{{ b.multiplier.toFixed(2) }}</td>
                </tr>
              </tbody>
            </table>
          </template>

          <template v-else-if="sec === 4">
            <div class="mb-2 text-[12px] text-ink-sub">偏离组与全市例均费用差 {{ n(t.waterfall.totalGap) }} 元的拆分(元)</div>
            <div class="relative grid h-[280px] grid-cols-4 gap-6 border-b border-line-strong px-5" data-testid="waterfall">
              <div v-for="(w, i) in t.waterfall.bars" :key="w.label" class="relative">
                <div class="absolute right-0 left-0 rounded-[2px]" :style="{ bottom: `${pct(w.base)}%`, height: `${pct(w.height)}%`, background: wfColor(i) }" />
                <div class="absolute right-0 left-0 text-center text-[12px] font-semibold text-ink" :style="{ bottom: `calc(${pct(w.base + w.height)}% + 4px)` }">
                  {{ w.kind === 'delta' ? signed(w.value) : n(w.value) }}
                </div>
              </div>
            </div>
            <div class="grid grid-cols-4 gap-6 px-5 pt-1.5 text-center text-[12px] text-ink-muted">
              <span v-for="w in t.waterfall.bars" :key="w.label">{{ w.label }}</span>
            </div>
            <div class="mt-3 text-[12px] text-ink-sub">
              患者差异(年龄、合并症、入院途径)解释 {{ t.waterfall.patientSharePct }}%;行为差异解释 {{ t.waterfall.behaviorSharePct }}%。多层回归 R² = {{ t.attribution.r2 }}。
            </div>
          </template>

          <template v-else-if="sec === 5">
            <table class="data-table">
              <thead><tr><th>指标</th><th class="text-right">标杆组({{ t.overview.benchOrgs }} 家)</th><th class="text-right">偏离组({{ t.overview.deviantOrgs }} 家)</th><th class="text-right">差距</th></tr></thead>
              <tbody>
                <tr v-for="b in t.benchmark" :key="b.metric">
                  <td class="text-ink">{{ b.metric }}</td>
                  <td class="text-right text-success">{{ b.bench }}</td>
                  <td class="text-right text-warning">{{ b.deviant }}</td>
                  <td class="text-right font-semibold text-ink">{{ b.gap }}</td>
                </tr>
              </tbody>
            </table>
            <div class="mt-2 text-[11px] text-ink-faint">机构以匿名编号呈现(三级医院A–I);具名版本仅医保局内部可见</div>
          </template>

          <template v-else-if="sec === 6">
            <div class="mb-3.5 rounded-lg border border-warning-line bg-warning-soft px-3 py-2.5 text-[12px] text-warning-ink" data-testid="opt-warning">
              理论测算结果,<b>不作为控费指标下达</b>,仅用于机构自我对照与医保侧规则评估。
            </div>
            <div class="text-[12px] text-ink-muted">理论优化空间(年化)</div>
            <div class="text-[30px] font-semibold text-ink">约 {{ n(t.optimization.totalWan) }} 万元</div>
            <div class="mt-1 text-[12px] text-ink-sub">= {{ t.optimization.formula }}</div>
            <div class="mt-4 grid grid-cols-3 gap-2.5">
              <div v-for="it in t.optimization.items" :key="it.name" class="rounded-lg border border-line px-3 py-2.5">
                <div class="text-[11px] text-ink-muted">{{ it.name }}</div><div class="font-semibold text-ink">约 {{ it.wan }} 万</div>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="grid grid-cols-2 gap-3 text-[12px]">
              <div class="rounded-[10px] border border-line p-3">
                <div class="mb-1.5 font-semibold text-primary">医保侧</div>
                <div v-for="(s, i) in t.suggestions.insurer" :key="s" class="text-ink">{{ i + 1 }}. {{ s }}</div>
              </div>
              <div class="rounded-[10px] border border-line p-3">
                <div class="mb-1.5 font-semibold text-success">医院侧</div>
                <div v-for="(s, i) in t.suggestions.hospital" :key="s" class="text-ink">{{ i + 1 }}. {{ s }}</div>
              </div>
            </div>
          </template>
        </Panel>

        <!-- 文稿区 -->
        <Panel v-if="cur" title="文稿区">
          <template #head>
            <Tag v-if="cur.approved" tone="success">已人工审定</Tag>
            <Tag v-else tone="warning">大模型初稿 · 待人工审定</Tag>
          </template>
          <div class="rounded-lg border border-line bg-subtle px-3 py-2.5 text-[13px] leading-[1.8] text-ink" data-testid="draft">{{ cur.draft }}</div>
          <div v-if="cur.approved && cur.approvedBy" class="mt-2 text-[11px] text-ink-faint">审定人:{{ cur.approvedBy }} · 第 {{ cur.draftVersion }} 版</div>
          <div v-if="!readOnly && !t.submitted" class="mt-2.5 flex gap-2">
            <Button size="sm" :disabled="busy || cur.approved" data-testid="approve-sec" @click="act('approve')">审定本段</Button>
            <Button size="sm" variant="outline" :disabled="busy" @click="act('regenerate')">重新生成</Button>
          </div>
          <div class="mt-2.5 text-[11px] text-ink-faint">
            {{ readOnly ? '专家组列席:只读审阅,不可审定或修改' : t.submitted ? '已提交审核,文稿已锁定' : '未审定段落不能进入发布包' }}
          </div>
        </Panel>
      </div>
    </template>
    <div v-else class="mt-5 h-[460px] animate-pulse rounded-2xl border border-line-soft bg-surface shadow-card" />
  </div>
</template>
