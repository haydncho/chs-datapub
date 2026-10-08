<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PageSection, PageHeader } from '@/components/yb'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getJson, usePageData, sendAction, runAction } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import { vPress } from '@/lib/a11y'
import { R, RS, AT, AS, INK } from '@/lib/palette'
import { A11_SEED, type A11Alert, type A11Data, type A11Evaluation, type AlertLevel, type AlertStatus } from '@/mock/A11'

const page = usePageData('A11', A11_SEED)

/** re-read the read model (status, 提醒函 date, 回执) after an accepted action */
async function refresh() {
  try {
    const r = await getJson<A11Data>('/pages/A11')
    if (r && typeof r === 'object') page.value = { ...A11_SEED, ...r }
  } catch { /* keep the current view */ }
}

/** live rule-engine run; `null` while loading / when the analytics service is unreachable (or not permitted) */
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
  .catch(() => { /* analytics not running / not permitted for this identity — keep the read model */ })

const data = computed<A11Data>(() => {
  const r = live.value
  if (!r) return page.value
  // the live run decides which alerts fire; the read model adds 提醒函 / 回执 state and per-alert notes
  const byId = new Map(page.value.alerts.map(a => [a.id, a]))
  return {
    ...page.value,
    ruleCount: r.ruleCount,
    months: r.months.length ? r.months : page.value.months,
    alerts: r.alerts.map(a => {
      const p = byId.get(a.id)
      const status = p?.status ?? (STATUSES.includes(a.status) ? a.status : 'unsent')
      return { ...a, status, attribution: p?.attribution ?? a.attribution, sentAt: p?.sentAt, sentBy: p?.sentBy, receiptDue: p?.receiptDue, receipt: p?.receipt }
    }),
    batch: r.batch,
    computedAt: r.computedAt,
  }
})

type Filter = 'all' | AlertLevel
const filter = ref<Filter>('all')
/** selected alert by id — the list swaps between seed, read model and live run */
const selId = ref<string>(page.value.alerts[0]?.id ?? '')
/** status accepted by the server in this session (until the next refresh brings it) */
const status = ref<Record<string, AlertStatus>>({})
const busy = ref(false)

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
const stOf = (a: A11Alert): AlertStatus => status.value[a.id] ?? a.status

/** 回执率 over the alerts on screen: 已回执 / (已发提醒函 + 已回执) */
const ackRate = computed(() => {
  const sent = alerts.value.filter(a => stOf(a) === 'sent').length
  const ack = alerts.value.filter(a => stOf(a) === 'ack').length
  return sent + ack ? Math.round((ack * 100) / (sent + ack)) : 0
})

const stats = computed(() => [
  { k: '本期触发', v: alerts.value.length, u: '条', c: 'text-ink-1' },
  { k: '高等级', v: alerts.value.filter(a => a.level === 'high').length, u: '条', c: 'text-bad' },
  { k: '未处置', v: alerts.value.filter(a => stOf(a) === 'unsent').length, u: '条', c: 'text-warn-ink' },
  { k: '回执率', v: ackRate.value, u: '%', c: 'text-ink-1' },
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
    .filter(a => filter.value === 'all' || a.level === filter.value)
    .map(a => {
      const L = LEVEL[a.level]
      const S = STATUS[stOf(a)]
      const mx = Math.max(...a.trend)
      const mn = Math.min(...a.trend)
      return {
        a,
        L,
        S,
        on: a.id === selId.value,
        sp: a.trend.map((x, j) => ({ h: (4 + ((x - mn) / (mx - mn || 1)) * 20).toFixed(0) + 'px', c: j === a.trend.length - 1 ? L.c : '#C7D7F7' })),
      }
    }),
)

// the detail always shows an alert of the visible list (filter ↔ detail in sync)
watch(list, rows => {
  if (rows.length && !rows.some(r => r.a.id === selId.value)) selId.value = rows[0]!.a.id
}, { immediate: true })

const cur = computed(() => alerts.value.find(a => a.id === selId.value) ?? alerts.value[0]!)
const curL = computed(() => LEVEL[cur.value.level])
const curSt = computed<AlertStatus>(() => stOf(cur.value))

const chart = computed(() => {
  const a = cur.value
  const mx = Math.max(...a.trend, a.thresholdValue) * 1.08
  const mn = Math.min(...a.trend, a.thresholdValue) * 0.92
  const yy = (v: number) => (1 - (v - mn) / (mx - mn || 1)) * 100
  const n = Math.max(1, a.trend.length - 1)
  const pts = a.trend.map((v, j) => [(j / n) * 100, yy(v)] as const)
  const line = pts.map(p => p[0].toFixed(2) + ' ' + p[1].toFixed(2)).join(' L')
  return {
    path: 'M' + line,
    area: 'M0 100 L' + line + ' L100 100 Z',
    th: yy(a.thresholdValue).toFixed(1) + '%',
    lastY: pts[pts.length - 1]![1].toFixed(1) + '%',
  }
})

const fmtN =(v: number) => (Math.abs(v) >= 100 ? Math.round(v).toLocaleString('zh-CN') : String(Math.round(v * 10) / 10))

/** 归因提示: the alert's own text, else derived from its trend */
const attribution = computed(() => {
  const a = cur.value
  if (a.attribution) return a.attribution
  const t = a.trend
  const last = t[t.length - 1]!
  const back = t[Math.max(0, t.length - 4)]!
  const over = t.filter(v => (a.kind === 'lt' ? v < a.thresholdValue : v > a.thresholdValue)).length
  const chg = back ? ((last - back) / Math.abs(back)) * 100 : 0
  return `归因提示:${a.org} ${a.metric} 近 3 月由 ${fmtN(back)} 变为 ${fmtN(last)}(${chg >= 0 ? '+' : ''}${chg.toFixed(1)}%),`
    + `近 ${t.length} 个月中有 ${over} 个月触发「${a.threshold}」。建议核实是否存在新开展术式、编码变化或患者结构变化。`
})

const sentNote = computed(() => {
  const a = cur.value
  if (!a.sentAt) return data.value.sentNote
  return `提醒函已于 ${a.sentAt} 发送${a.sentBy ? `(${a.sentBy})` : ''} · 机构需在 ${a.receiptDue ?? '10 个工作日内'}${a.receiptDue ? ' 前' : ''}回执说明原因与整改措施`
})
const ackNote = computed(() => {
  const r = cur.value.receipt
  if (!r) return data.value.ackNote
  return `已回执${r.at ? ' ' + r.at : ''}${r.category ? ` · ${r.category}` : ''}:${r.text || '(未填写说明)'} 下期自动复查。`
})

async function send() {
  if (busy.value) return
  const a = cur.value
  busy.value = true
  const r = await runAction('A11', 'sendReminder', { alertId: a.id, org: a.org })
  busy.value = false
  if (!r.ok) {
    say(r.error)
    return
  }
  status.value = { ...status.value, [a.id]: 'sent' }
  say('提醒函已发送至 ' + a.org + ' · 要求 10 个工作日内回执')
  void refresh()
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
  <PageSection label="A11 预警提醒" class="max-xl:[&_.text-\[10px\]]:text-[11px] max-xl:[&_.text-\[11px\]]:text-xs">
    <PageHeader title="预警提醒" :subtitle="`${data.period} · 规则 ${data.ruleCount} 条 · ${data.scanTime}`">
      <template v-if="data.batch" #subtitle>
        {{ `${data.period} · 规则 ${data.ruleCount} 条 · ${data.scanTime}` }}<Badge variant="ok" class="ml-2 align-[1px] max-lg:mt-1.5 max-lg:ml-0 max-lg:flex max-lg:w-fit">实时计算 · 批次 {{ data.batch }}</Badge>
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

    <div class="grid grid-cols-1 items-start gap-4 lg:max-xl:grid-cols-[minmax(0,1fr)_360px] xl:grid-cols-[minmax(0,1fr)_minmax(400px,480px)]">
      <!-- alert list -->
      <div class="yb-card min-w-0 overflow-hidden">
        <div class="flex flex-wrap gap-1.5 border-b border-line-2 px-4 py-3 max-xl:gap-2" role="group" aria-label="按等级筛选">
          <button
            v-for="c in chips"
            :key="c.id"
            type="button"
            :aria-pressed="c.on"
            :class="cn(
              'cursor-pointer rounded-full border px-3 py-1 text-xs whitespace-nowrap max-xl:inline-flex max-xl:min-h-10 max-xl:items-center max-xl:px-4',
              c.on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
            )"
            @click="filter = c.id"
          >{{ c.l }}</button>
        </div>
        <div
          v-for="r in list"
          :key="r.a.id"
          v-press
          :aria-current="r.on ? 'true' : undefined"
          :aria-label="`${r.L.l}等级 · ${r.a.metric} · ${r.a.org} · ${r.S.l}`"
          :class="cn(
            'grid cursor-pointer grid-cols-[28px_minmax(0,1fr)_auto] items-center gap-x-3.5 gap-y-1 yb-tr border-b border-line-3 px-4 py-3.5 max-xl:min-h-14 sm:max-lg:grid-cols-[28px_minmax(0,1.2fr)_72px_90px_120px] xl:grid-cols-[28px_minmax(0,1.2fr)_72px_90px_120px]',
            r.on ? 'bg-brand-tint shadow-[inset_3px_0_0_var(--brand)]' : 'bg-white hover:bg-surface-1',
          )"
          @click="selId = r.a.id"
        >
          <span
            class="flex size-6 items-center justify-center rounded-md text-xs font-bold"
            :style="{ background: r.L.b, color: r.L.c }"
          >{{ r.L.l }}</span>
          <div class="min-w-0">
            <div class="truncate font-semibold">{{ r.a.metric }}</div>
            <div class="truncate text-xs text-ink-4">{{ r.a.org }} · 阈值 {{ r.a.threshold }}</div>
          </div>
          <div class="flex h-6 items-end gap-0.5 max-sm:hidden lg:max-xl:hidden" aria-hidden="true">
            <span v-for="(b, j) in r.sp" :key="j" class="w-1 rounded-[1px]" :style="{ height: b.h, background: b.c }" />
          </div>
          <span class="yb-num text-right text-lg font-semibold whitespace-nowrap" :style="{ color: r.L.c }">{{ r.a.value }}</span>
          <span class="flex items-center gap-[5px] text-[11px] whitespace-nowrap max-sm:col-start-2 max-sm:col-end-4 lg:max-xl:col-start-2 lg:max-xl:col-end-4" :style="{ color: r.S.c }">
            <span class="size-1.5 shrink-0 rounded-full" :style="{ background: r.S.c }" />{{ r.S.l }}
          </span>
        </div>
        <div v-if="!list.length" class="p-10 text-center text-sm text-ink-5">该等级暂无预警</div>
      </div>

      <!-- detail -->
      <div class="yb-card flex min-w-0 flex-col gap-4 px-[22px] py-5 max-xl:px-5 max-sm:px-4 lg:max-xl:sticky lg:max-xl:top-(--sticky-panel)">
        <div>
          <div class="flex items-center gap-2">
            <span class="rounded px-2 py-px text-[11px] font-semibold" :style="{ background: curL.b, color: curL.c }">{{ curL.l }}等级</span>
            <span class="text-xs text-ink-4">{{ cur.org }}</span>
          </div>
          <div class="mt-1.5 text-lg font-semibold">{{ cur.metric }}</div>
          <div class="flex flex-wrap items-baseline gap-x-2.5">
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

        <div class="rounded-[10px] bg-surface-1 px-3.5 py-3 text-xs leading-[1.7] text-pretty text-ink-2">{{ attribution }}</div>

        <div v-if="curSt === 'unsent'" class="flex flex-wrap gap-2">
          <Button class="h-9 flex-1 max-xl:h-11" :disabled="busy" @click="send">发送提醒函</Button>
          <Button variant="outline" class="h-9 px-3.5 font-normal max-xl:h-11" @click="toTopic">转专题选题</Button>
          <Button variant="outline" class="h-9 px-3.5 font-normal text-ink-4 max-xl:h-11" @click="ignore">忽略</Button>
        </div>
        <div v-if="curSt === 'sent'" class="rounded-[10px] bg-brand-soft px-3.5 py-3 text-xs leading-[1.7] text-brand">{{ sentNote }}</div>
        <div v-if="curSt === 'ack'" class="rounded-[10px] bg-ok-soft px-3.5 py-3 text-xs leading-[1.7] text-ok-ink">{{ ackNote }}</div>
      </div>
    </div>
  </PageSection>
</template>
