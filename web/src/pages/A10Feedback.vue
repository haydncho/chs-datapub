<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { usePageData } from '@/api/client'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import { A, AT, BRAND, G, GT, INK, R } from '@/lib/palette'
import { A10_SEED, type FeedbackStatus } from '@/mock/A10'
import FeedbackDetail from './A10/FeedbackDetail.vue'
import { FBS, FBT } from './A10/meta'
import { vPress } from '@/lib/a11y'

const data = usePageData('A10', A10_SEED)

/** local status / assignee overrides keyed by item id */
const fst = reactive<Record<string, FeedbackStatus>>({})
const who = reactive<Record<string, string>>({})
const q = ref<'all' | FeedbackStatus>('all')
const fsel = ref(0)

const stOf = (i: number) => {
  const it = data.value.items[i]!
  return fst[it.id] ?? it.status
}

const QUEUES: ['all' | FeedbackStatus, string][] = [
  ['all', '全部'], ['todo', '待分派'], ['over', '已超期'], ['doing', '处理中'], ['reply', '待答复'], ['done', '已答复'],
]
const queues = computed(() =>
  QUEUES.map(([id, label]) => {
    const n = data.value.items.filter((_, i) => id === 'all' || stOf(i) === id).length
    return { id, label, n, on: id === q.value, alarm: id === 'over' && n > 0 }
  }),
)

const kpis = computed(() => {
  const items = data.value.items
  const open = items.filter((_, i) => stOf(i) !== 'done').length
  const over = items.filter((_, i) => stOf(i) === 'over').length
  const s = data.value.stats
  return [
    { k: '未闭环', v: String(open), u: '条', c: open ? AT : GT },
    { k: '已超期', v: String(over), u: '条', c: over ? R : GT },
    { k: '答复率', v: s.replyRate, u: '%', c: INK[0] },
    { k: '平均答复时长', v: s.avgReplyDays, u: '天', c: INK[0] },
    { k: '引发更正', v: s.corrections, u: '次', c: INK[0] },
  ]
})

const rows = computed(() =>
  data.value.items
    .map((f, i) => ({ f, i }))
    .filter(({ i }) => q.value === 'all' || stOf(i) === q.value)
    .map(({ f, i }) => {
      const st = stOf(i)
      const done = st === 'done'
      const d = f.daysLeft
      return {
        f,
        i,
        st,
        on: i === fsel.value,
        sla: done ? '已闭环' : d < 0 ? `超期 ${-d} 天` : d === 0 ? '今日到期' : `剩 ${d} 天`,
        slaC: done ? GT : d < 0 ? R : d <= 2 ? AT : INK[3],
        slaW: done ? '100%' : `${Math.max(6, Math.min(100, ((7 - d) / 7) * 100))}%`,
        slaBar: done ? G : d < 0 ? R : d <= 2 ? A : BRAND,
      }
    }),
)

const selItem = computed(() => {
  const it = data.value.items[fsel.value]!
  return { ...it, assignee: who[it.id] ?? it.assignee }
})
const selStatus = computed(() => stOf(fsel.value))

function select(i: number) {
  fsel.value = i
}
function setStatus(id: string, st: FeedbackStatus) {
  fst[id] = st
}
function setAssignee(id: string, name: string) {
  who[id] = name
}
</script>

<template>
  <section data-screen-label="A10 意见与申诉" class="flex min-h-[calc(100vh-132px)] flex-col">
    <!-- title + SLA KPIs -->
    <div class="flex items-center gap-7 border-b border-line-1 bg-white px-7 py-[18px]">
      <div class="shrink-0 whitespace-nowrap">
        <div class="text-[22px] font-semibold">意见与申诉</div>
        <div class="text-xs text-ink-4">机构反馈统一受理 · 时限 5 个工作日 · 答复同步机构端</div>
      </div>
      <div class="flex flex-1 justify-end">
        <div v-for="k in kpis" :key="k.k" class="border-l border-line-2 px-5 whitespace-nowrap">
          <div class="text-xs text-ink-4">{{ k.k }}</div>
          <div>
            <span class="yb-num text-2xl font-semibold" :style="{ color: k.c }">{{ k.v }}</span>
            <span class="text-[11px] text-ink-4"> {{ k.u }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid flex-1 grid-cols-[180px_minmax(0,1fr)_minmax(380px,460px)]">
      <!-- status queues -->
      <aside class="flex flex-col gap-0.5 border-r border-line-1 bg-surface-1 px-2.5 py-4">
        <div v-press
          v-for="x in queues"
          :key="x.id"
          :class="cn(
            'flex cursor-pointer justify-between rounded-lg px-2.5 py-2',
            x.on ? 'bg-brand-soft font-semibold text-brand' : 'font-normal text-ink-2',
          )"
          @click="q = x.id"
        >
          <span>{{ x.label }}</span>
          <span :class="cn('yb-num font-semibold', x.alarm ? 'text-bad' : 'text-ink-5')">{{ x.n }}</span>
        </div>
      </aside>

      <!-- item list -->
      <div class="min-w-0 border-r border-line-1 bg-white">
        <div v-if="!rows.length" class="p-12 text-center text-ink-5">此队列暂无事项</div>
        <div v-press
          v-for="r in rows"
          :key="r.f.id"
          :class="cn(
            'flex cursor-pointer flex-col gap-1.5 border-b border-line-3 px-5 py-3.5 hover:bg-surface-1',
            r.on ? 'bg-brand-tint shadow-[inset_3px_0_0_var(--brand)]' : 'bg-white',
          )"
          @click="select(r.i)"
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
        :key="selItem.id"
        :item="selItem"
        :status="selStatus"
        :templates="data.templates"
        :assign-to="data.assignTo"
        :track="data.track"
        :just-assigned="selItem.id in who"
        @status="setStatus"
        @assign="setAssignee"
      />
    </div>
  </section>
</template>
