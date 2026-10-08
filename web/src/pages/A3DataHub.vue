<script setup lang="ts">
import { computed, ref } from 'vue'
import { PageHeader, PageSection, StatCard, type StatTone } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { getJson, runAction, usePageData } from '@/api/client'
import { goPage } from '@/app/router'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import { A3_SEED, type A3Data } from '@/mock/A3'
import SourceTable from './A3/SourceTable.vue'
import SourceDetail, { type A3PullState } from './A3/SourceDetail.vue'
import type { A3Row } from './A3/types'

const data = usePageData('A3', A3_SEED)

/** re-read the page read model after an accepted action (server state is the source of truth) */
async function refresh() {
  try {
    const remote = await getJson<A3Data>('/pages/A3')
    if (remote && typeof remote === 'object') data.value = { ...A3_SEED, ...remote }
  } catch { /* keep the current view */ }
}

// ── state: arrival / quality check come from the server read model ──
const qcDone = computed(() => !!data.value.qcDone)
// default selection: the first late source (异地就医 in the seed)
const sel = ref(Math.max(0, A3_SEED.sources.findIndex(s => s.status === 'late')))
const pull = ref<A3PullState>('idle')
const busy = ref(false)

// ── derived ──
const rows = computed<A3Row[]>(() =>
  data.value.sources.map(src => ({ src, status: src.status, history: src.history })),
)
const lateRows = computed(() => rows.value.filter(r => r.status === 'late'))
const arrived = computed(() => lateRows.value.length === 0)
const nIn = computed(() => rows.value.length - lateRows.value.length)
const nSrc = computed(() => rows.value.length)
const suspended = computed(() => lateRows.value.reduce((n, r) => n + r.src.dependents.length, 0))
const indTotal = computed(() => data.value.stats.indicatorTotal)
const indCalc = computed(() => `${indTotal.value - suspended.value}/${indTotal.value}`)
const late = computed(() => lateRows.value.length > 0)
/** 本期数据量 = Σ rowsWan of the sources that have arrived (a late source contributes nothing yet) */
const totalWan = computed(() => rows.value.filter(r => r.status !== 'late').reduce((n, r) => n + r.src.rowsWan, 0))
const totalRows = computed(() => totalWan.value.toLocaleString('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + ' 万')

const ICONS = [
  'M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3',
  'M4 20V11M10 20V5M16 20v-6M21 20H3',
  'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3zM8.5 12l2.5 2.5 4.5-5',
  'M12 3l9 5-9 5-9-5 9-5zM3 13l9 5 9-5',
]
const SUB_COLOR: Record<StatTone, string> = { ok: 'var(--ok-ink)', warn: 'var(--warn-ink)', bad: 'var(--bad-ink)', info: 'var(--ink-3)' }

const kpis = computed(() => {
  const st = data.value.stats
  const list: { label: string; value: string; sub: string; tone: StatTone }[] = [
    {
      label: '数据源到数',
      value: `${nIn.value}/${nSrc.value}`,
      sub: late.value ? lateRows.value.map(r => r.src.name).join('、') + ' 未按时到达' : '全部到达',
      tone: late.value ? 'warn' : 'ok',
    },
    {
      label: '指标可计算',
      value: indCalc.value,
      sub: suspended.value ? `${suspended.value} 项本期暂缓` : '全部可计算',
      tone: suspended.value ? 'warn' : 'ok',
    },
    { label: '综合质量分', value: st.qualityScore, sub: `较上期 ${st.qualityDelta}`, tone: 'info' },
    { label: '本期数据量', value: totalRows.value, sub: `行 · ${nIn.value} 个已到数据源合计`, tone: 'info' },
  ]
  return list.map((k, i) => ({ ...k, icon: ICONS[i]!, subColor: SUB_COLOR[k.tone] }))
})

type PipeState = 'done' | 'run' | 'wait'
const PIPE_STYLE: Record<PipeState, { bg: string; fg: string; label: string }> = {
  done: { bg: 'bg-ok-soft', fg: 'text-ok', label: '完成' },
  run: { bg: 'bg-brand-soft', fg: 'text-brand', label: '进行中' },
  wait: { bg: 'bg-line-3', fg: 'text-ink-5', label: '等待' },
}
const pipe = computed(() => {
  const st = data.value.stats
  const qc = qcDone.value
  const steps: [string, string, string, PipeState][] = [
    ['接入', `${nIn.value}/${nSrc.value} 源`, `${totalRows.value}行`, late.value ? 'run' : 'done'],
    ['标准化', `${nIn.value}/${nIn.value}`, `字段映射 ${st.fieldMappings}`, 'done'],
    ['主数据对齐', `${st.orgs} 机构`, `医师 ${st.doctors} · 病组 ${st.drgGroups}`, 'done'],
    ['质量校验', qc ? '通过' : '进行中', `规则 ${st.rules} 条`, qc ? 'done' : 'run'],
    ['主题库', `${st.domains} 域`, qc ? '已刷新' : '等待校验', qc ? 'done' : 'wait'],
    ['指标集市', indCalc.value, suspended.value ? `${suspended.value} 项本期暂缓` : '全部可计算', qc ? 'done' : 'wait'],
  ]
  return steps.map(([n, v, sub, s]) => ({ n, v, sub, ...PIPE_STYLE[s] }))
})

const selRow = computed(() => rows.value[sel.value] ?? rows.value[0]!)
function selectRow(i: number) {
  sel.value = i
  pull.value = 'idle'
}

const qcSpec = computed(() => data.value.qcSpec)
const listScale = (v: number) => ((v - 80) / 20) * 100
const orgQc = computed(() =>
  data.value.orgQc.map(q => ({
    ...q,
    aw: q.comorbidityRate + '%',
    aWarn: q.comorbidityRate < qcSpec.value.comorbidityThreshold,
    b: q.listQcRate.toFixed(1),
    bw: listScale(q.listQcRate) + '%',
    bWarn: q.listQcRate < qcSpec.value.listQcThreshold,
  })),
)
const rules = computed(() => {
  const max = Math.max(1, ...data.value.rules.map(r => r.hits))
  return data.value.rules.map(r => ({ ...r, w: (r.hits / max) * 100 + '%' }))
})

// ── actions: the view changes only after the server accepted them ──
interface PullResult { result?: { arrived: boolean; status?: number; trace?: string; at?: string; dependents?: number } }

async function onPull() {
  if (pull.value === 'load') return
  const source = selRow.value.src.name
  pull.value = 'load'
  const r = await runAction<PullResult>('A3', 'retryPull', { source })
  if (!r.ok) {
    pull.value = 'idle'
    say(r.error)
    return
  }
  await refresh()
  const res = r.data?.result
  if (res?.arrived) {
    pull.value = 'idle'
    say(`重试成功 · ${source}数据已到达,${res.dependents ?? 0} 项指标恢复计算`)
  } else {
    pull.value = 'err'
  }
}
async function onNotify() {
  const source = selRow.value.src.name
  const r = await runAction('A3', 'notifyContact', { source, channels: ['政务微信', '短信'] })
  if (!r.ok) return say(r.error)
  await refresh()
  say('已通知省平台对接人 · 政务微信 + 短信')
}
async function onQc() {
  if (busy.value) return
  busy.value = true
  const r = await runAction('A3', 'completeQualityCheck', { period: data.value.period })
  busy.value = false
  if (!r.ok) return say(r.error)
  await refresh()
  say('质量校验通过,指标集市已刷新')
}
async function onGen() {
  if (!qcDone.value) {
    say('请先完成数据到数与质量校验')
    return
  }
  if (busy.value) return
  busy.value = true
  const r = await runAction<{ result?: { created: boolean; title: string } }>('A3', 'generateMonthlyReport', { period: data.value.period })
  busy.value = false
  if (!r.ok) return say(r.error)
  await refresh()
  const res = r.data?.result
  say(res && !res.created
    ? `${data.value.periodShort}月度报告草稿已在发布工作流中(${res.title})`
    : `已生成 ${data.value.periodShort}月度报告草稿,进入发布工作流`)
  setTimeout(() => goPage('A8'), 800)
}
</script>

<template>
  <PageSection label="A3 数据归集中心">
    <PageHeader title="数据归集中心" :subtitle="`${data.period} · ${nSrc} 类数据源 · 应到截止 ${data.dueDate}`">
      <span class="flex h-9 items-center gap-1.5 rounded-lg bg-surface-3 px-3.5 text-ink-3 whitespace-nowrap" title="数据归集按账期进行,仅可操作当前账期">当前账期 <b class="font-medium text-ink-1">{{ data.period }}</b></span>
      <span v-if="qcDone" class="flex h-9 items-center text-xs text-ok-ink whitespace-nowrap">✓ 质量校验已完成{{ data.qcAt ? ' · ' + data.qcAt : '' }}</span>
      <Button v-if="arrived && !qcDone" variant="soft" class="font-medium" :disabled="busy" @click="onQc">完成质量校验</Button>
      <Button :class="cn(!qcDone && 'bg-brand-mute')" :disabled="busy" @click="onGen">{{ data.reportTaskId ? '查看月度报告草稿 →' : '生成月度报告 →' }}</Button>
    </PageHeader>

    <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <StatCard
        v-for="k in kpis"
        :key="k.label"
        :label="k.label"
        :value="k.value"
        :sub="k.sub"
        :sub-color="k.subColor"
        :tone="k.tone"
        :icon="k.icon"
      />
    </div>

    <!-- 数据管道 -->
    <div class="yb-card px-card-x py-card-y-sm">
      <div class="mb-3 flex justify-between">
        <span class="text-sm font-semibold">数据管道</span>
        <span class="text-xs text-ink-4">接入 → 指标集市 · {{ data.stats.schedule }}</span>
      </div>
      <div class="grid grid-cols-3 gap-2 max-lg:gap-y-3 lg:grid-cols-6 xl:flex xl:items-stretch">
        <div v-for="(p, i) in pipe" :key="p.n" class="flex min-w-0 flex-1 items-center gap-2">
          <div :class="cn('min-w-0 flex-1 rounded-[10px] px-3.5 py-3', p.bg)">
            <div class="flex items-center justify-between">
              <span class="text-xs font-medium text-ink-3">{{ p.n }}</span>
              <span :class="cn('text-[11px] font-semibold', p.fg)">{{ p.label }}</span>
            </div>
            <div class="yb-num mt-0.5 text-[22px] font-semibold text-ink-1">{{ p.v }}</div>
            <div class="truncate text-[11px] text-ink-4">{{ p.sub }}</div>
          </div>
          <span v-if="i < pipe.length - 1" class="text-ink-6 max-xl:hidden">→</span>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(300px,340px)]">
      <SourceTable :rows="rows" :selected="sel" @select="selectRow" />
      <div class="flex flex-col gap-4">
        <SourceDetail :row="selRow" :pull="pull" @pull="onPull" @notify="onNotify" />
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
      <!-- 机构编码质量 -->
      <div class="yb-card px-card-x py-card-y">
        <div class="mb-3 flex flex-wrap justify-between gap-x-4 gap-y-1">
          <span class="flex items-center gap-2">
            <span class="text-sm font-semibold">机构编码质量</span>
            <span class="rounded-full bg-surface-3 px-2 py-px font-mono text-[11px] whitespace-nowrap text-ink-3">口径 {{ qcSpec.version }} · {{ qcSpec.batch }}</span>
          </span>
          <span class="text-xs text-ink-4">门槛:合并症编码率 {{ qcSpec.comorbidityThreshold }}% · 清单质控率 {{ qcSpec.listQcThreshold }}%</span>
        </div>
        <div class="overflow-x-auto"><div class="min-w-[460px]">
        <div class="grid grid-cols-[150px_1fr_1fr] gap-4 pb-1.5 text-[11px] text-ink-5">
          <span>机构</span><span>合并症编码率</span><span>结算清单质控率</span>
        </div>
        <div v-for="q in orgQc" :key="q.org" class="grid grid-cols-[150px_1fr_1fr] items-center gap-4 py-1.5 text-xs">
          <span>{{ q.org }}</span>
          <div class="flex items-center gap-2">
            <div class="relative h-2 flex-1 rounded bg-line-2">
              <div :class="cn('h-2 rounded', q.aWarn ? 'bg-warn' : 'bg-brand')" :style="{ width: q.aw }" />
              <span class="absolute -top-[3px] -bottom-[3px] border-l-[1.5px] border-dashed border-ink-4" :style="{ left: qcSpec.comorbidityThreshold + '%' }" />
            </div>
            <span class="yb-num w-[30px] text-right font-semibold">{{ q.comorbidityRate }}%</span>
          </div>
          <div class="flex items-center gap-2">
            <div class="relative h-2 flex-1 rounded bg-line-2">
              <div :class="cn('h-2 rounded', q.bWarn ? 'bg-warn' : 'bg-ok')" :style="{ width: q.bw }" />
              <span class="absolute -top-[3px] -bottom-[3px] border-l-[1.5px] border-dashed border-ink-4" :style="{ left: listScale(qcSpec.listQcThreshold) + '%' }" />
            </div>
            <span class="yb-num w-9 text-right font-semibold">{{ q.b }}%</span>
          </div>
        </div>
        </div></div>
      </div>

      <!-- 质量规则命中 -->
      <div class="yb-card px-card-x py-card-y">
        <div class="mb-3 flex justify-between">
          <span class="text-sm font-semibold">质量规则命中 Top 5</span>
          <span class="text-xs text-ink-4">规则 {{ data.stats.rules }} 条</span>
        </div>
        <div v-for="r in rules" :key="r.id" class="border-t border-line-3 py-2">
          <div class="flex justify-between text-xs">
            <span>{{ r.name }} <span class="font-mono text-[11px] text-ink-5">{{ r.id }}</span></span>
            <span class="yb-num font-semibold">{{ r.hits }}</span>
          </div>
          <div class="mt-1.5 h-1 rounded-[2px] bg-line-2">
            <div class="h-1 rounded-[2px] bg-warn" :style="{ width: r.w }" />
          </div>
        </div>
      </div>
    </div>
  </PageSection>
</template>
