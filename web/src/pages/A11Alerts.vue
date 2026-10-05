<script setup lang="ts">
import { computed, ref } from 'vue'
import { PageSection, PageHeader } from '@/components/yb'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getJson, usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import { R, RS, AT, AS, INK } from '@/lib/palette'
import { A11_SEED, type A11Alert, type A11Data, type A11Evaluation, type AlertLevel, type AlertStatus } from '@/mock/A11'

const page = usePageData('A11', A11_SEED)

/** live rule-engine run; `null` while loading / when the analytics service is unreachable */
const live = ref<A11Evaluation | null>(null)
const LEVELS: AlertLevel[] = ['high', 'mid', 'low']
const STATUSES: AlertStatus[] = ['unsent', 'sent', 'ack']
const isAlert = (a: unknown): a is A11Alert => {
  const x = a as Partial<A11Alert> | null
  return !!x && typeof x.id === 'string' && typeof x.metric === 'string' && LEVELS.includes(x.level as AlertLevel)
    && Array.isArray(x.trend) && x.trend.length > 1 && typeof x.thresholdValue === 'number'
}
getJson<A11Evaluation>('/analytics/alerts/evaluate')
  .then(r => {
    // an empty run keeps the seed (the screen has no empty state)
    if (r && Array.isArray(r.alerts) && r.alerts.length > 0 && r.alerts.every(isAlert)) live.value = r
  })
  .catch(() => { /* analytics not running — keep the seed */ })

const data = computed<A11Data>(() => {
  const r = live.value
  if (!r) return page.value
  return {
    ...page.value,
    ruleCount: r.ruleCount,
    months: r.months.length ? r.months : page.value.months,
    alerts: r.alerts.map(a => ({ ...a, status: STATUSES.includes(a.status) ? a.status : 'unsent' })),
    batch: r.batch,
    computedAt: r.computedAt,
  }
})

type Filter = 'all' | AlertLevel
const filter = ref<Filter>('all')
const sel = ref(0)
/** local status overrides by alert id (after 发送提醒函) — survives the live list swapping in */
const status = ref<Record<string, AlertStatus>>({})

const LEVEL: Record<AlertLevel, { l: string; c: string; b: string }> = {
  high: { l: '高', c: R, b: RS },
  mid: { l: '中', c: AT, b: AS },
  low: { l: '低', c: INK[2], b: '#F1F3F6' },
}
const STATUS: Record<AlertStatus, { l: string; c: string }> = {
  unsent: { l: '未处置', c: INK[4] },
  sent: { l: '提醒函已发 · 待回执', c: 'var(--brand)' },
  ack: { l: '已回执 · 整改中', c: '#0F7A4D' },
}

const alerts = computed(() => data.value.alerts)
const stOf = (i: number): AlertStatus => {
  const a = alerts.value[i]!
  return status.value[a.id] ?? a.status
}

const stats = computed(() => [
  { k: '本期触发', v: alerts.value.length, u: '条', c: 'text-ink-1' },
  { k: '高等级', v: alerts.value.filter(a => a.level === 'high').length, u: '条', c: 'text-bad' },
  { k: '未处置', v: alerts.value.filter((_, i) => stOf(i) === 'unsent').length, u: '条', c: 'text-warn-ink' },
  { k: '回执率', v: data.value.ackRate, u: '%', c: 'text-ink-1' },
])

const chips = computed(() =>
  ([['all', '全部'], ['high', '高'], ['mid', '中'], ['low', '低']] as [Filter, string][]).map(([id, l]) => ({
    id,
    l: `${l} ${id === 'all' ? alerts.value.length : alerts.value.filter(a => a.level === id).length}`,
    on: id === filter.value,
  })),
)

const list = computed(() =>
  alerts.value
    .map((a, i) => ({ a, i }))
    .filter(({ a }) => filter.value === 'all' || a.level === filter.value)
    .map(({ a, i }) => {
      const L = LEVEL[a.level]
      const S = STATUS[stOf(i)]
      const mx = Math.max(...a.trend)
      const mn = Math.min(...a.trend)
      return {
        i,
        a,
        L,
        S,
        on: i === sel.value,
        sp: a.trend.map((x, j) => ({ h: (4 + ((x - mn) / (mx - mn || 1)) * 20).toFixed(0) + 'px', c: j === a.trend.length - 1 ? L.c : '#C7D7F7' })),
      }
    }),
)

const cur = computed(() => alerts.value[sel.value] ?? alerts.value[0]!)
const curL = computed(() => LEVEL[cur.value.level])
const curSt = computed<AlertStatus>(() => status.value[cur.value.id] ?? cur.value.status)

const chart = computed(() => {
  const a = cur.value
  const mx = Math.max(...a.trend, a.thresholdValue) * 1.08
  const mn = Math.min(...a.trend, a.thresholdValue) * 0.92
  const yy = (v: number) => (1 - (v - mn) / (mx - mn)) * 100
  const n = a.trend.length - 1
  const pts = a.trend.map((v, j) => [(j / n) * 100, yy(v)] as const)
  const line = pts.map(p => p[0].toFixed(2) + ' ' + p[1].toFixed(2)).join(' L')
  return {
    path: 'M' + line,
    area: 'M0 100 L' + line + ' L100 100 Z',
    th: yy(a.thresholdValue).toFixed(1) + '%',
    lastY: pts[n]![1].toFixed(1) + '%',
  }
})

function send() {
  const a = cur.value
  status.value = { ...status.value, [a.id]: 'sent' }
  sendAction('A11', 'sendReminder', { alertId: a.id, org: a.org })
  say('提醒函已发送至 ' + a.org + ' · 要求 10 个工作日内回执')
}
function toTopic() {
  sendAction('A11', 'addToTopic', { alertId: cur.value.id, metric: cur.value.metric })
  say('已加入选题池 → 智能推荐')
}
function ignore() {
  say('忽略需填写理由并经召集人确认')
}
</script>

<template>
  <PageSection label="A11 预警提醒">
    <PageHeader title="预警提醒" :subtitle="`${data.period} · 规则 ${data.ruleCount} 条 · ${data.scanTime}`">
      <template v-if="data.batch" #subtitle>
        {{ `${data.period} · 规则 ${data.ruleCount} 条 · ${data.scanTime}` }}<Badge variant="ok" class="ml-2 align-[1px]">实时计算 · 批次 {{ data.batch }}</Badge>
      </template>
      <div class="flex max-w-full overflow-x-auto rounded-[var(--radius-card)] border border-line-1 bg-white">
        <div v-for="k in stats" :key="k.k" class="border-l border-line-2 px-[22px] py-2.5 whitespace-nowrap max-xl:px-4 max-xl:first:border-l-0">
          <div class="text-xs text-ink-4">{{ k.k }}</div>
          <div>
            <span :class="cn('yb-num text-[22px] font-semibold', k.c)">{{ k.v }}</span>
            <span class="text-[11px] text-ink-4"> {{ k.u }}</span>
          </div>
        </div>
      </div>
    </PageHeader>

    <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(400px,480px)]">
      <!-- alert list -->
      <div class="yb-card overflow-hidden">
        <div class="flex flex-wrap gap-1.5 border-b border-line-2 px-4 py-3 max-xl:gap-2">
          <span
            v-for="c in chips"
            :key="c.id"
            :class="cn(
              'cursor-pointer rounded-full border px-3 py-1 text-xs whitespace-nowrap max-xl:inline-flex max-xl:min-h-10 max-xl:items-center max-xl:px-4',
              c.on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
            )"
            @click="filter = c.id"
          >{{ c.l }}</span>
        </div>
        <div
          v-for="r in list"
          :key="r.a.id"
          :class="cn(
            'grid cursor-pointer grid-cols-[28px_minmax(0,1.2fr)_72px_90px_120px] items-center gap-3.5 border-b border-line-3 px-4 py-3.5 max-xl:min-h-14',
            r.on ? 'bg-brand-tint shadow-[inset_3px_0_0_var(--brand)]' : 'bg-white hover:bg-surface-1',
          )"
          @click="sel = r.i"
        >
          <span
            class="flex size-6 items-center justify-center rounded-md text-xs font-bold"
            :style="{ background: r.L.b, color: r.L.c }"
          >{{ r.L.l }}</span>
          <div class="min-w-0">
            <div class="font-semibold">{{ r.a.metric }}</div>
            <div class="text-xs text-ink-4">{{ r.a.org }} · 阈值 {{ r.a.threshold }}</div>
          </div>
          <div class="flex h-6 items-end gap-0.5">
            <span v-for="(b, j) in r.sp" :key="j" class="w-1 rounded-[1px]" :style="{ height: b.h, background: b.c }" />
          </div>
          <span class="yb-num text-right text-lg font-semibold" :style="{ color: r.L.c }">{{ r.a.value }}</span>
          <span class="flex items-center gap-[5px] text-[11px]" :style="{ color: r.S.c }">
            <span class="size-1.5 rounded-full" :style="{ background: r.S.c }" />{{ r.S.l }}
          </span>
        </div>
      </div>

      <!-- detail -->
      <div class="yb-card flex flex-col gap-4 px-[22px] py-5">
        <div>
          <div class="flex items-center gap-2">
            <span class="rounded px-2 py-px text-[11px] font-semibold" :style="{ background: curL.b, color: curL.c }">{{ curL.l }}等级</span>
            <span class="text-xs text-ink-4">{{ cur.org }}</span>
          </div>
          <div class="mt-1.5 text-lg font-semibold">{{ cur.metric }}</div>
          <div class="flex items-baseline gap-2.5">
            <span class="yb-num text-[34px] font-semibold" :style="{ color: curL.c }">{{ cur.value }}</span>
            <span class="text-xs text-ink-4">触发条件 {{ cur.threshold }}</span>
          </div>
        </div>

        <div>
          <div class="relative mx-1 h-40">
            <svg viewBox="0 0 100 100" preserveAspectRatio="none" class="absolute inset-0 size-full overflow-visible">
              <path :d="chart.area" fill="var(--brand)" fill-opacity="0.08" />
              <path :d="chart.path" fill="none" stroke="var(--brand)" stroke-width="2" vector-effect="non-scaling-stroke" />
            </svg>
            <div class="absolute inset-x-0 border-t-[1.5px] border-dashed border-bad" :style="{ top: chart.th }">
              <span class="absolute right-0 -top-[18px] bg-white px-1 text-[11px] text-bad">阈值</span>
            </div>
            <span
              class="absolute -mt-[5px] -ml-[5px] size-2.5 rounded-full border-2 border-white"
              :style="{ left: '100%', top: chart.lastY, background: curL.c, boxShadow: `0 0 0 1px ${curL.c}` }"
            />
          </div>
          <div class="yb-num mt-1.5 flex justify-between text-[11px] text-ink-5">
            <span v-for="m in data.months" :key="m">{{ m }}</span>
          </div>
        </div>

        <div class="rounded-[10px] bg-surface-1 px-3.5 py-3 text-xs leading-[1.7] text-ink-2">{{ data.attribution }}</div>

        <div v-if="curSt === 'unsent'" class="flex gap-2">
          <Button class="h-9 flex-1 max-xl:h-10" @click="send">发送提醒函</Button>
          <Button variant="outline" class="h-9 px-3.5 font-normal max-xl:h-10" @click="toTopic">转专题选题</Button>
          <Button variant="outline" class="h-9 px-3.5 font-normal text-ink-4 max-xl:h-10" @click="ignore">忽略</Button>
        </div>
        <div v-if="curSt === 'sent'" class="rounded-[10px] bg-brand-soft px-3.5 py-3 text-xs text-brand">{{ data.sentNote }}</div>
        <div v-if="curSt === 'ack'" class="rounded-[10px] bg-ok-soft px-3.5 py-3 text-xs leading-[1.7] text-ok-ink">{{ data.ackNote }}</div>
      </div>
    </div>
  </PageSection>
</template>
