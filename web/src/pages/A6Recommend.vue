<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { goPage } from '@/app/router'
import { say } from '@/app/shell'
import { session } from '@/app/session'
import { getJson, runAction, usePageData } from '@/api/client'
import { vPress } from '@/lib/a11y'
import { A6_SEED, type A6Data, type A6Kind, type A6Status, type A6Topic } from '@/mock/A6'
import { mergeTopics, useLiveTopics } from './A6/liveTopics'

const page = usePageData('A6', A6_SEED)
/** 病种专题 scored live by the analytics service (falls back to the page seed) */
const live = useLiveTopics(4)
const data = computed<A6Data>(() =>
  live.value
    ? { ...page.value, topics: mergeTopics(page.value.topics, live.value), batch: live.value[0]?.batch }
    : page.value,
)

/** re-read the read model: 采纳 / 本期不做 decisions are server state */
async function refresh() {
  try {
    const remote = await getJson<A6Data>('/pages/A6')
    if (remote && typeof remote === 'object') page.value = { ...A6_SEED, ...remote }
  } catch { /* keep the current view */ }
}

/** 推荐仅供参考,由行政管理组(及召集人)采纳 — other identities only look */
const DECIDERS = ['convener', 'admin']
const role = computed(() => session.current?.identity.role ?? null)
const canDecide = computed(() => role.value === null || DECIDERS.includes(role.value))

const filter = ref('全部')
/** selected topic id (defaults to the first topic of the current filter) */
const selId = ref<string | null>(null)
const busy = ref(false)

const KIND_CLS: Record<A6Kind, string> = {
  病种专题: 'bg-brand-soft text-brand',
  机构专题: 'bg-bad-soft text-bad-ink',
  质量专题: 'bg-warn-soft text-warn-ink',
  区域专题: 'bg-violet-soft text-violet',
}
const STATUS: Record<A6Status, { label: string; cls: string }> = {
  adopted: { label: '已采纳', cls: 'bg-ok-soft text-ok-ink' },
  open: { label: '待评估', cls: 'bg-brand-soft text-brand' },
  skip: { label: '本期不做', cls: 'bg-line-3 text-ink-4' },
}
const DIM_KEYS = ['impact', 'deviation', 'actionable', 'ready'] as const

const statusOf = (t: A6Topic): A6Status => data.value.topicStates?.[t.id] ?? t.status
const scoreCls = (s: number) => (s >= 85 ? 'text-brand' : s >= 70 ? 'text-ink-1' : 'text-ink-4')

const topics = computed(() =>
  data.value.topics.filter(t => filter.value === '全部' || t.kind === filter.value),
)
/** the selection follows the filter: a topic outside the current list is not shown in the detail */
const current = computed<A6Topic | undefined>(
  () => topics.value.find(t => t.id === selId.value) ?? topics.value[0],
)
const curStatus = computed(() => (current.value ? statusOf(current.value) : 'open'))
const subtitle = computed(() => `${data.value.batch ? '基于实时计算批次' : data.value.period} · ${data.value.subtitle}`)

const method = computed(() => {
  const m = current.value?.method
  if (!m || !current.value) return null
  const terms = DIM_KEYS.map((k, j) => `${m.weights[k].toFixed(2)}×${data.value.dimLabels[j]} ${current.value!.dims[j]}`)
  const i = m.inputs
  return {
    formula: `${terms.join(' + ')} = ${current.value.score}`,
    inputs: [
      ['病例数', i.cases.toLocaleString('zh-CN')],
      ['例均差额', (i.diffPerCase > 0 ? '+' : i.diffPerCase < 0 ? '−' : '') + Math.abs(i.diffPerCase).toLocaleString('zh-CN') + ' 元'],
      ['次均费用', i.costPerCase.toLocaleString('zh-CN') + ' 元'],
      ['偏离度', i.deviationPct.toFixed(1) + '%'],
    ],
  }
})

const detailEl = ref<HTMLElement | null>(null)
function pick(id: string) {
  selId.value = id
  // 竖屏(<1024)详情在列表下方,点选后滚动到详情;≥1024 详情并排吸顶,无需滚动
  if (window.innerWidth < 1024) nextTick(() => detailEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

let navT: ReturnType<typeof setTimeout> | undefined
onBeforeUnmount(() => clearTimeout(navT))

function payloadOf(t: A6Topic) {
  return { id: t.id, title: t.title, kind: t.kind, score: t.score, facts: t.facts }
}
async function decide(action: 'adoptTopic' | 'skipTopic' | 'reopenTopic') {
  const t = current.value
  if (!t || busy.value) return
  busy.value = true
  const r = await runAction('A6', action, payloadOf(t))
  busy.value = false
  if (!r.ok) {
    say(r.error)
    return
  }
  await refresh()
  if (action === 'adoptTopic') {
    say('已采纳,正在打开专题工作台')
    navT = setTimeout(() => goPage('A7', { topic: t.id }), 700)
  } else if (action === 'skipTopic') {
    say(`已标记「${t.title}」本期不做 · 下期自动重新评估`)
  } else {
    say(`已恢复「${t.title}」为待评估`)
  }
}
function openWorkbench() {
  if (current.value) goPage('A7', { topic: current.value.id })
}
</script>

<template>
  <PageSection label="A6 智能推荐">
    <!-- 竖屏:筛选标签另起一行,副标题不被挤断 -->
    <PageHeader title="智能推荐 · 选题池" :subtitle="subtitle" class="max-lg:flex-col max-lg:items-stretch max-lg:[&>div:first-child]:basis-auto">
      <template v-if="data.batch" #subtitle>
        {{ subtitle }}<Badge variant="ok" class="ml-2 align-[1px]">实时计算 · 批次 {{ data.batch }}</Badge>
      </template>
      <div class="flex flex-wrap gap-1.5" role="group" aria-label="选题类别筛选">
        <button
          v-for="f in data.filters"
          :key="f"
          type="button"
          :aria-pressed="f === filter"
          :class="cn(
            'cursor-pointer rounded-full border px-3.5 py-1.5 text-xs whitespace-nowrap max-xl:flex max-xl:min-h-10 max-xl:items-center',
            f === filter ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
          )"
          @click="filter = f"
        >{{ f }}</button>
      </div>
    </PageHeader>

    <!-- ≥1024 列表与详情左右并排(详情吸顶);竖屏上下排列,点选后滚动到详情 -->
    <div class="grid grid-cols-1 items-start gap-4 lg:grid-cols-[minmax(0,1fr)_340px] xl:grid-cols-[minmax(0,1fr)_380px]">
      <!-- candidate list -->
      <div class="flex flex-col gap-2.5">
        <div
          v-if="topics.length === 0"
          class="flex flex-col items-center gap-1.5 rounded-xl border border-dashed border-line-4 bg-white px-5 py-11 text-ink-4"
        >
          <div class="font-semibold text-ink-2">该类别暂无候选选题</div>
          <div class="text-xs">下期数据归集后将自动重新评估</div>
        </div>
        <div
          v-for="t in topics"
          :key="t.id"
          v-press
          :aria-pressed="t.id === current?.id"
          :aria-label="`${t.title} · 得分 ${t.score} · ${STATUS[statusOf(t)].label}`"
          :class="cn(
            'grid cursor-pointer grid-cols-[64px_minmax(0,1fr)_260px_110px] items-center gap-5 rounded-xl border bg-white px-5 py-4 hover:border-brand-line focus-visible:outline-2 focus-visible:outline-brand max-xl:grid-cols-[64px_minmax(0,1fr)_auto] max-xl:gap-x-4 max-xl:gap-y-3',
            t.id === current?.id ? 'border-brand shadow-[0_0_0_3px_var(--brand-soft)] hover:border-brand' : 'border-line-1',
          )"
          @click="pick(t.id)"
        >
          <div class="text-center max-xl:row-span-2">
            <div :class="cn('yb-num text-[26px] leading-none font-semibold', scoreCls(t.score))">{{ t.score }}</div>
            <div class="mt-0.5 text-[11px] whitespace-nowrap text-ink-5 max-xl:text-xs">综合得分</div>
          </div>
          <div class="min-w-0">
            <div class="flex items-center gap-1.5">
              <span :class="cn('rounded px-[7px] py-px text-[11px] font-semibold whitespace-nowrap max-xl:text-xs', KIND_CLS[t.kind])">{{ t.kind }}</span>
              <span class="truncate text-[11px] text-ink-5 max-xl:text-xs">来源 {{ t.source }}</span>
            </div>
            <div class="mt-1 text-[15px] font-semibold">{{ t.title }}</div>
            <div class="truncate text-xs text-ink-4 max-xl:line-clamp-2 max-xl:whitespace-normal">{{ t.why }}</div>
          </div>
          <div class="flex flex-col gap-1 max-xl:col-span-2 max-xl:col-start-2 max-xl:row-start-2 max-xl:grid max-xl:grid-cols-2 max-xl:gap-x-6 max-xl:gap-y-1.5">
            <div
              v-for="(v, j) in t.dims"
              :key="j"
              class="grid grid-cols-[64px_1fr_22px] items-center gap-2 text-[11px] max-xl:grid-cols-[52px_1fr_24px] max-xl:text-xs"
            >
              <span class="whitespace-nowrap text-ink-4">{{ data.dimLabels[j] }}</span>
              <div class="h-[5px] rounded-[3px] bg-line-2">
                <div class="h-[5px] rounded-[3px] bg-[#5B8FD9]" :style="{ width: v + '%' }" />
              </div>
              <span class="yb-num text-right text-ink-3">{{ v }}</span>
            </div>
          </div>
          <div class="text-right max-xl:col-start-3 max-xl:row-start-1">
            <span :class="cn('rounded-full px-2.5 py-0.5 text-[11px] font-medium whitespace-nowrap max-xl:text-xs', STATUS[statusOf(t)].cls)">{{ STATUS[statusOf(t)].label }}</span>
          </div>
        </div>
      </div>

      <!-- detail -->
      <div v-if="current" ref="detailEl" class="scroll-mt-20 lg:sticky lg:top-(--sticky-panel) flex flex-col gap-3.5 rounded-xl border border-line-1 bg-white p-5">
        <div>
          <div class="text-xs text-ink-4">{{ current.kind }} · 得分 {{ current.score }}</div>
          <div class="mt-0.5 text-[17px] font-semibold">{{ current.title }}</div>
        </div>
        <div class="text-[13px] leading-[1.75] text-ink-2">{{ current.why }}</div>
        <div class="grid grid-cols-2 gap-2 md:max-lg:grid-cols-4">
          <div v-for="f in current.facts" :key="f.k" class="min-w-0 rounded-[10px] bg-surface-1 px-3 py-2.5">
            <div class="text-[11px] text-ink-4 max-xl:text-xs">{{ f.k }}</div>
            <div class="yb-num text-[19px] font-semibold whitespace-nowrap">{{ f.v }}</div>
          </div>
        </div>
        <div v-if="method" class="rounded-[10px] border border-line-2 px-3 py-2.5 text-xs">
          <div class="mb-1 font-medium text-ink-2">方法卡 · 得分如何算出</div>
          <div class="leading-[1.6] text-ink-3">{{ method.formula }}</div>
          <div class="mt-1.5 grid grid-cols-2 gap-x-3 gap-y-0.5 text-[11px] text-ink-4 max-xl:text-xs md:max-lg:grid-cols-4">
            <span v-for="[k, v] in method.inputs" :key="k">{{ k }} <b class="yb-num font-medium text-ink-2">{{ v }}</b></span>
          </div>
        </div>
        <div>
          <div class="mb-1.5 text-xs text-ink-4">建议受众</div>
          <div class="flex flex-wrap gap-1.5">
            <span v-for="a in current.audiences" :key="a" class="rounded-lg bg-brand-soft px-2.5 py-1 text-xs text-brand">{{ a }}</span>
          </div>
        </div>
        <template v-if="curStatus === 'open'">
          <div class="flex gap-2 max-xl:flex-wrap">
            <Button class="h-9 flex-1 whitespace-nowrap max-xl:h-11" :disabled="!canDecide || busy" @click="decide('adoptTopic')">采纳 · 进入专题工作台</Button>
            <Button variant="outline" class="h-9 px-3.5 font-normal whitespace-nowrap text-ink-4 max-xl:h-11" :disabled="!canDecide || busy" @click="decide('skipTopic')">本期不做</Button>
          </div>
          <div v-if="!canDecide" class="text-xs text-ink-4">推荐仅供参考,由行政管理组采纳 · 当前身份仅可查看</div>
        </template>
        <div v-else-if="curStatus === 'adopted'" class="flex items-center justify-between gap-2 rounded-[10px] bg-ok-soft px-3 py-2.5 text-xs text-ok-ink">
          <span>✓ 已采纳 · 专题工作台进行中</span>
          <button type="button" class="cursor-pointer font-medium text-brand hover:underline max-xl:min-h-10" @click="openWorkbench">进入专题工作台 →</button>
        </div>
        <div v-else class="flex items-center justify-between gap-2 rounded-[10px] bg-line-3 px-3 py-2.5 text-xs text-ink-3">
          <span>本期不做 · 下期自动重新评估</span>
          <button v-if="canDecide" type="button" :disabled="busy" class="cursor-pointer font-medium text-brand hover:underline max-xl:min-h-10" @click="decide('reopenTopic')">撤销 · 恢复待评估</button>
        </div>
      </div>
    </div>
  </PageSection>
</template>
