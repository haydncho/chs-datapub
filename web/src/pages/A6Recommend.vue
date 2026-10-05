<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { goPage } from '@/app/router'
import { say } from '@/app/shell'
import { sendAction, usePageData } from '@/api/client'
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

const filter = ref('全部')
/** selected topic id (defaults to the first topic) */
const selId = ref<string | null>(null)
/** local status overrides after 采纳 / 本期不做 */
const overrides = reactive<Record<string, A6Status>>({})

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

const statusOf = (t: A6Topic) => overrides[t.id] ?? t.status
const scoreCls = (s: number) => (s >= 85 ? 'text-brand' : s >= 70 ? 'text-ink-1' : 'text-ink-4')

const topics = computed(() =>
  data.value.topics.filter(t => filter.value === '全部' || t.kind === filter.value),
)
const current = computed<A6Topic | undefined>(
  () => data.value.topics.find(t => t.id === selId.value) ?? data.value.topics[0],
)
const curStatus = computed(() => (current.value ? statusOf(current.value) : 'open'))

const detailEl = ref<HTMLElement | null>(null)
function pick(id: string) {
  selId.value = id
  // 窄屏下详情在列表下方,点选后滚动到详情
  if (window.innerWidth < 1280) nextTick(() => detailEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

let navT: ReturnType<typeof setTimeout> | undefined
onBeforeUnmount(() => clearTimeout(navT))

function adopt() {
  const t = current.value
  if (!t) return
  overrides[t.id] = 'adopted'
  sendAction('A6', 'adoptTopic', { id: t.id, title: t.title })
  say('已采纳,正在打开专题工作台')
  navT = setTimeout(() => goPage('A7'), 700)
}
function skip() {
  const t = current.value
  if (!t) return
  overrides[t.id] = 'skip'
  sendAction('A6', 'skipTopic', { id: t.id, title: t.title })
}
</script>

<template>
  <PageSection label="A6 智能推荐">
    <PageHeader title="智能推荐 · 选题池" :subtitle="data.subtitle">
      <template v-if="data.batch" #subtitle>
        {{ data.subtitle }}<Badge variant="ok" class="ml-2 align-[1px]">实时计算 · 批次 {{ data.batch }}</Badge>
      </template>
      <div class="flex gap-1.5">
        <span
          v-for="f in data.filters"
          :key="f"
          :class="cn(
            'cursor-pointer rounded-full border px-3.5 py-1.5 text-xs whitespace-nowrap max-xl:flex max-xl:min-h-10 max-xl:items-center',
            f === filter ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
          )"
          @click="filter = f"
        >{{ f }}</span>
      </div>
    </PageHeader>

    <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
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
          :class="cn(
            'grid cursor-pointer grid-cols-[64px_minmax(0,1fr)_260px_110px] items-center gap-5 rounded-xl border bg-white px-5 py-4 hover:border-brand-line max-xl:grid-cols-[64px_minmax(0,1fr)_auto] max-xl:gap-x-4 max-xl:gap-y-3',
            t.id === current?.id ? 'border-brand shadow-[0_0_0_3px_var(--brand-soft)] hover:border-brand' : 'border-line-1',
          )"
          @click="pick(t.id)"
        >
          <div class="text-center max-xl:row-span-2">
            <div :class="cn('yb-num text-[32px] leading-none font-semibold', scoreCls(t.score))">{{ t.score }}</div>
            <div class="mt-0.5 text-[11px] text-ink-5">综合得分</div>
          </div>
          <div class="min-w-0">
            <div class="flex items-center gap-1.5">
              <span :class="cn('rounded px-[7px] py-px text-[11px] font-semibold', KIND_CLS[t.kind])">{{ t.kind }}</span>
              <span class="text-[11px] text-ink-5">来源 {{ t.source }}</span>
            </div>
            <div class="mt-1 text-[15px] font-semibold">{{ t.title }}</div>
            <div class="truncate text-xs text-ink-4">{{ t.why }}</div>
          </div>
          <div class="flex flex-col gap-1 max-xl:col-span-2 max-xl:col-start-2 max-xl:row-start-2">
            <div
              v-for="(v, j) in t.dims"
              :key="j"
              class="grid grid-cols-[64px_1fr_22px] items-center gap-2 text-[11px]"
            >
              <span class="text-ink-4">{{ data.dimLabels[j] }}</span>
              <div class="h-[5px] rounded-[3px] bg-line-2">
                <div class="h-[5px] rounded-[3px] bg-[#5B8FD9]" :style="{ width: v + '%' }" />
              </div>
              <span class="yb-num text-right text-ink-3">{{ v }}</span>
            </div>
          </div>
          <div class="text-right max-xl:col-start-3 max-xl:row-start-1">
            <span :class="cn('rounded-full px-2.5 py-0.5 text-[11px] font-medium', STATUS[statusOf(t)].cls)">{{ STATUS[statusOf(t)].label }}</span>
          </div>
        </div>
      </div>

      <!-- detail -->
      <div v-if="current" ref="detailEl" class="scroll-mt-20 xl:sticky xl:top-(--sticky-panel) flex flex-col gap-3.5 rounded-xl border border-line-1 bg-white p-5">
        <div>
          <div class="text-xs text-ink-4">{{ current.kind }} · 得分 {{ current.score }}</div>
          <div class="mt-0.5 text-[17px] font-semibold">{{ current.title }}</div>
        </div>
        <div class="text-[13px] leading-[1.75] text-ink-2">{{ current.why }}</div>
        <div class="grid grid-cols-2 gap-2">
          <div v-for="f in current.facts" :key="f.k" class="rounded-[10px] bg-surface-1 px-3 py-2.5">
            <div class="text-[11px] text-ink-4">{{ f.k }}</div>
            <div class="yb-num text-[19px] font-semibold">{{ f.v }}</div>
          </div>
        </div>
        <div>
          <div class="mb-1.5 text-xs text-ink-4">建议受众</div>
          <div class="flex flex-wrap gap-1.5">
            <span v-for="a in current.audiences" :key="a" class="rounded-lg bg-brand-soft px-2.5 py-1 text-xs text-brand">{{ a }}</span>
          </div>
        </div>
        <div v-if="curStatus === 'open'" class="flex gap-2">
          <Button class="h-9 flex-1 max-xl:h-10" @click="adopt">采纳 · 进入专题工作台</Button>
          <Button variant="outline" class="h-9 px-3.5 font-normal text-ink-4 max-xl:h-10" @click="skip">本期不做</Button>
        </div>
        <div v-else class="rounded-[10px] bg-ok-soft px-3 py-2.5 text-xs text-ok-ink">
          {{ curStatus === 'adopted' ? '✓ 已采纳 · 专题工作台进行中' : '本期不做 · 下期自动重新评估' }}
        </div>
      </div>
    </div>
  </PageSection>
</template>
