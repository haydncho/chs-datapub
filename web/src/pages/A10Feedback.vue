<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getJson, usePageData } from '@/api/client'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import { A, AT, BRAND, G, GT, INK, R } from '@/lib/palette'
import { A10_SEED, type A10Data, type FeedbackStatus } from '@/mock/A10'
import FeedbackDetail from './A10/FeedbackDetail.vue'
import { FBS, FBT } from './A10/meta'
import { vPress } from '@/lib/a11y'

const data = usePageData('A10', A10_SEED)

/** re-read the queue after an accepted action (status, track, KPIs all come from the server) */
async function refresh() {
  try {
    const r = await getJson<A10Data>('/pages/A10')
    if (r && typeof r === 'object') data.value = { ...A10_SEED, ...r }
  } catch { /* keep the current view */ }
}

const q = ref<'all' | FeedbackStatus>('all')
/** selection by item id — the server list may reorder / grow (B5 / C3 items) */
const selId = ref<string>(data.value.items[0]?.id ?? '')

const QUEUES: ['all' | FeedbackStatus, string][] = [
  ['all', '全部'], ['todo', '待分派'], ['over', '已超期'], ['doing', '处理中'], ['reply', '待答复'], ['done', '已答复'],
]
const queues = computed(() =>
  QUEUES.map(([id, label]) => {
    const n = data.value.items.filter(it => id === 'all' || it.status === id).length
    return { id, label, n, on: id === q.value, alarm: id === 'over' && n > 0 }
  }),
)

const kpis = computed(() => {
  const items = data.value.items
  const open = items.filter(it => it.status !== 'done').length
  const over = items.filter(it => it.status === 'over').length
  const s = data.value.stats
  return [
    { k: '未闭环', v: String(open), u: '条', c: open ? AT : GT },
    { k: '已超期', v: String(over), u: '条', c: over ? R : GT },
    { k: '答复率', v: s.replyRate, u: '%', c: INK[0] },
    { k: '平均答复时长', v: s.avgReplyDays, u: '天', c: INK[0] },
    { k: '引发更正', v: s.corrections, u: '次', c: INK[0] },
  ]
})

const slaDays = computed(() => data.value.stats.slaDays ?? 5)

const rows = computed(() =>
  data.value.items
    .filter(f => q.value === 'all' || f.status === q.value)
    .map(f => {
      const st = f.status
      const done = st === 'done'
      const d = f.daysLeft
      return {
        f,
        st,
        on: f.id === selId.value,
        sla: done ? '已闭环' : d < 0 ? `超期 ${-d} 天` : d === 0 ? '今日到期' : `剩 ${d} 天`,
        slaC: done ? GT : d < 0 ? R : d <= 2 ? AT : INK[3],
        slaW: done ? '100%' : `${Math.max(6, Math.min(100, ((slaDays.value - d) / slaDays.value) * 100))}%`,
        slaBar: done ? G : d < 0 ? R : d <= 2 ? A : BRAND,
      }
    }),
)

// keep the detail in sync with the visible list: a filter that hides the selection selects the first visible item
watch(rows, list => {
  if (list.length && !list.some(r => r.f.id === selId.value)) selId.value = list[0]!.f.id
}, { immediate: true })

const selItem = computed(() => {
  const items = data.value.items
  return rows.value.find(r => r.f.id === selId.value)?.f ?? items.find(it => it.id === selId.value) ?? items[0]!
})

function select(id: string) {
  selId.value = id
}
</script>

<template>
  <section data-screen-label="A10 意见与申诉" class="flex min-h-[calc(100vh-132px)] flex-col">
    <!-- title + SLA KPIs -->
    <div class="flex items-center gap-7 border-b border-line-1 bg-white px-7 py-[18px] max-xl:flex-col max-xl:items-stretch max-xl:gap-3">
      <div class="shrink-0 whitespace-nowrap">
        <div class="text-[22px] font-semibold">意见与申诉</div>
        <div class="text-xs text-ink-4">机构反馈统一受理 · 时限 5 个工作日 · 答复同步机构端</div>
      </div>
      <div class="flex flex-1 justify-end max-xl:justify-start max-xl:overflow-x-auto max-xl:pb-1">
        <div v-for="k in kpis" :key="k.k" class="border-l border-line-2 px-5 whitespace-nowrap max-xl:first:border-l-0 max-xl:first:pl-0">
          <div class="text-xs text-ink-4">{{ k.k }}</div>
          <div>
            <span class="yb-num text-2xl font-semibold" :style="{ color: k.c }">{{ k.v }}</span>
            <span class="text-[11px] text-ink-4"> {{ k.u }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid flex-1 grid-cols-1 content-start xl:grid-cols-[180px_minmax(0,1fr)_minmax(380px,460px)]">
      <!-- status queues -->
      <aside class="flex flex-col gap-0.5 border-r border-line-1 bg-surface-1 px-2.5 py-4 max-xl:flex-row max-xl:gap-2 max-xl:overflow-x-auto max-xl:border-r-0 max-xl:border-b max-xl:px-4 max-xl:py-2.5">
        <div v-press
          v-for="x in queues"
          :key="x.id"
          :class="cn(
            'flex cursor-pointer justify-between rounded-lg px-2.5 py-2 max-xl:min-h-10 max-xl:shrink-0 max-xl:items-center max-xl:gap-2 max-xl:whitespace-nowrap',
            x.on ? 'bg-brand-soft font-semibold text-brand' : 'font-normal text-ink-2',
          )"
          @click="q = x.id"
        >
          <span>{{ x.label }}</span>
          <span :class="cn('yb-num font-semibold', x.alarm ? 'text-bad' : 'text-ink-5')">{{ x.n }}</span>
        </div>
      </aside>

      <!-- item list -->
      <div class="min-w-0 border-r border-line-1 bg-white max-xl:max-h-[420px] max-xl:overflow-y-auto max-xl:border-r-0 max-xl:border-b">
        <div v-if="!rows.length" class="p-12 text-center text-ink-5">此队列暂无事项</div>
        <div v-press
          v-for="r in rows"
          :key="r.f.id"
          :class="cn(
            'flex min-h-10 cursor-pointer flex-col gap-1.5 border-b border-line-3 px-5 py-3.5 hover:bg-surface-1',
            r.on ? 'bg-brand-tint shadow-[inset_3px_0_0_var(--brand)]' : 'bg-white',
          )"
          :aria-current="r.on ? 'true' : undefined"
          @click="select(r.f.id)"
        >
          <div class="flex items-center gap-2">
            <span
              class="rounded px-[7px] py-px text-[11px] font-semibold"
              :style="{ background: FBT[r.f.type][0], color: FBT[r.f.type][1] }"
            >{{ r.f.type }}</span>
            <span class="text-xs text-ink-3">{{ r.f.org }}</span>
            <div class="flex-1" />
            <span class="font-mono text-[11px] text-ink-5">{{ r.f.id }}</span>
          </div>
          <div class="text-sm font-semibold">{{ r.f.title }}</div>
          <div class="flex items-center gap-3 text-xs">
            <span class="min-w-0 flex-1 truncate text-ink-4">{{ r.f.section }}</span>
            <Badge :variant="FBS[r.st].variant" class="border-0 py-px">{{ FBS[r.st].label }}</Badge>
            <div class="flex w-[130px] items-center gap-1.5">
              <div class="h-1 flex-1 rounded-sm bg-line-2">
                <div class="h-1 rounded-sm" :style="{ width: r.slaW, background: r.slaBar }" />
              </div>
              <span class="text-[11px] font-medium whitespace-nowrap" :style="{ color: r.slaC }">{{ r.sla }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- detail -->
      <FeedbackDetail
        v-if="selItem"
        :key="selItem.id + selItem.status + (selItem.assignee || '')"
        :item="selItem"
        :status="selItem.status"
        :templates="data.templates"
        :assign-to="data.assignTo"
        :track="data.track"
        @changed="refresh"
      />
    </div>
  </section>
</template>
