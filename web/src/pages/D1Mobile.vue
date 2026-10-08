<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { getJson, runAction, usePageData, sendAction } from '@/api/client'
import { goPage } from '@/app/router'
import { session } from '@/app/session'
import { cn } from '@/lib/utils'
import { R, A, V, INK } from '@/lib/palette'
import { D1_SEED, type D1Data, type D1Screen, type D1Target } from '@/mock/D1'
import { vPress } from '@/lib/a11y'

const data = usePageData('D1', D1_SEED)

// ---- state: sign-off / receipt come from the server (per institution); local flags only bridge until the reload
const screen = ref<D1Screen>('home')
const signedNow = ref(false)
const rcNow = ref(false)
const reviewAsked = ref(false)
const busy = ref(false)
const signed = computed(() => signedNow.value || data.value.report.status === 'signed')
const rcDone = computed(() => rcNow.value || data.value.receiptDone === true)
/** 未批准不外发: the report is only offered once it was released to this institution */
const reportOut = computed(() => data.value.report.status !== 'none')
/** only the institution's own identity signs / answers (the server refuses everyone else) */
const canSign = computed(() => data.value.canSign ?? session.current?.identity.role === 'hospital')

async function refresh() {
  try {
    const remote = await getJson<D1Data>('/pages/D1')
    if (remote && typeof remote === 'object') data.value = { ...D1_SEED, ...remote }
  } catch { /* keep what is shown */ }
}
const cat = ref<number | null>(null)
const txt = ref('')
const doneT = ref('')
const doneS = ref('')

// phone-local toast (1.6s, centred in the phone)
const mToast = ref('')
let mt: ReturnType<typeof setTimeout> | undefined
function mSay(t: string) {
  mToast.value = t
  clearTimeout(mt)
  mt = setTimeout(() => (mToast.value = ''), 1600)
}
onBeforeUnmount(() => clearTimeout(mt))

const go = (x: D1Screen) => {
  screen.value = x
}
function open(t: D1Target) {
  if (t === 'B5') goPage('B5')
  else go(t)
}

const BACK: Partial<Record<D1Screen, D1Screen>> = { report: 'home', alert: 'home', rcpt: 'alert', done: 'home' }
const TITLES: Partial<Record<D1Screen, string>> = { report: '月度报告', alert: '预警详情', rcpt: '填写回执', done: '' }
const back = computed(() => BACK[screen.value])
const showBack = computed(() => !!back.value && screen.value !== 'done')
const showTabs = computed(() => ['home', 'msgs', 'me'].includes(screen.value))

const TONE: Record<string, string> = { warn: A, bad: R, violet: V, brand: 'var(--brand)', muted: INK[4] }

const todos = computed(() =>
  data.value.todos
    .filter(t => t.id !== 'sign' || reportOut.value)
    .map(t => {
      const isDone = t.id === 'sign' ? signed.value : t.id === 'receipt' ? rcDone.value : false
      return { ...t, isDone, c: isDone ? INK[5] : TONE[t.tone] }
    }),
)
const pend = computed(() => todos.value.filter(t => !t.isDone).length)

const messages = computed(() =>
  data.value.messages.filter(m => m.task !== 'sign' || reportOut.value).map(m => {
    const isDone = m.task === 'sign' ? signed.value : m.task === 'receipt' ? rcDone.value : false
    return { ...m, t: isDone && m.doneTime ? m.doneTime : m.time, c: TONE[m.tone] }
  }),
)
function openMsg(m: (typeof messages.value)[number]) {
  if (m.target) open(m.target)
  else {
    sendAction('D1', 'markRead', { message: m.label })
    mSay('已读')
  }
}

/** unread task messages (报告待签收 / 提醒函待回执) for the tab badge */
const unread = computed(() => messages.value.filter(m => m.task && !(m.task === 'sign' ? signed.value : rcDone.value)).length)

const meRows = computed(() =>
  data.value.me.rows.map(r =>
    r.k === '签收记录' ? { ...r, v: r.v || (data.value.reportSignoff?.signedReports.length ?? (signed.value ? 1 : 0)) + ' 份' } : r,
  ),
)

const bars = computed(() => {
  const b = data.value.alert.bars
  return b.map((v, i) => ({ h: v + '%', c: i === b.length - 1 ? R : '#F3B4AE' }))
})

const rcValid = computed(() => cat.value != null && !!txt.value.trim())

const cta = computed<null | { t: string; muted: boolean; fn: () => void }>(() => {
  const m = screen.value
  if (m === 'report') return signed.value || !reportOut.value || !canSign.value ? null : { t: busy.value ? '签收中…' : '确认签收', muted: busy.value, fn: sign }
  if (m === 'alert') return rcDone.value || !canSign.value ? null : { t: '填写回执说明', muted: false, fn: () => go('rcpt') }
  if (m === 'rcpt') return { t: busy.value ? '提交中…' : '提交回执', muted: !rcValid.value || busy.value, fn: submitReceipt }
  if (m === 'done') return { t: '返回首页', muted: false, fn: () => go('home') }
  return null
})

async function sign() {
  if (busy.value || signed.value) return
  busy.value = true
  const r = await runAction('D1', 'signReport', { reportId: data.value.report.id, report: data.value.report.title, version: 'v1' })
  busy.value = false
  if (!r.ok) {
    mSay(r.error || '签收未成功')
    void refresh()
    return
  }
  signedNow.value = true
  doneT.value = '已签收'
  doneS.value = data.value.report.title + ' · 签收时间已记录\n召集人可在签收追踪中看到'
  screen.value = 'done'
  void refresh()
}
async function submitReceipt() {
  if (!rcValid.value) {
    mSay('请选择原因类别并填写说明')
    return
  }
  if (busy.value) return
  busy.value = true
  const r = await runAction('D1', 'submitReceipt', {
    alertId: data.value.alert.id,
    alert: data.value.alert.title,
    category: data.value.receiptCategories[cat.value!],
    text: txt.value.trim(),
  })
  busy.value = false
  if (!r.ok) {
    mSay(r.error || '回执未提交成功')
    return
  }
  rcNow.value = true
  doneT.value = '回执已提交'
  doneS.value = '医保局将在 5 个工作日内答复\n可在消息中查看进度'
  screen.value = 'done'
  void refresh()
}
async function requestReview() {
  if (busy.value || reviewAsked.value) return
  busy.value = true
  const r = await runAction('D1', 'requestReview', { alert: data.value.alert.title })
  busy.value = false
  if (!r.ok) {
    mSay(r.error || '复核申请未提交')
    return
  }
  reviewAsked.value = true
  mSay('已提交复核申请')
}

const IPS: Record<string, string> = {
  home: 'M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  report: 'M6 3h9l5 5v13H6zM14 3v6h6',
  msgs: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
  me: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c0-4 3.6-7 8-7s8 3 8 7',
}
const TABS: [string, D1Screen][] = [['概览', 'home'], ['报告', 'report'], ['消息', 'msgs'], ['我的', 'me']]
</script>

<template>
  <section data-screen-label="D1 移动端" class="mx-auto flex w-full max-w-[1600px] flex-wrap justify-center gap-10 px-8 pt-7 pb-14">
    <!-- phone -->
    <div class="h-[780px] w-[375px] shrink-0 rounded-[44px] bg-chrome p-3 shadow-[0_20px_50px_rgba(11,21,38,.25)]">
      <div class="relative flex size-full flex-col overflow-hidden rounded-[34px] bg-background">
        <!-- status bar -->
        <div class="yb-num flex h-11 shrink-0 items-center justify-between bg-white px-[26px] text-sm font-semibold">
          <span>9:41</span><span class="h-[26px] w-[100px] rounded-[13px] bg-chrome" /><span>5G</span>
        </div>
        <!-- nav bar -->
        <div v-if="showBack" class="flex h-12 shrink-0 items-center gap-1.5 border-b border-line-2 bg-white px-3">
          <button type="button" class="flex size-11 cursor-pointer items-center justify-center text-2xl text-ink-3" aria-label="返回" @click="go(back ?? 'home')">‹</button>
          <span class="text-base font-semibold">{{ TITLES[screen] }}</span>
        </div>

        <div class="flex flex-1 flex-col gap-2.5 overflow-y-auto px-3.5 py-3">
          <!-- home -->
          <template v-if="screen === 'home'">
            <div class="px-1 pt-0.5 pb-1">
              <div class="text-xs text-ink-4">{{ data.hospital }} · {{ data.period }}</div>
              <div class="text-[22px] font-semibold">本院医保运行</div>
            </div>
            <div v-if="data.noOwnData" class="rounded-2xl bg-white px-4 py-10 text-center"><div class="text-base font-semibold text-ink-2">暂无本院数据</div><div class="mt-1.5 text-xs text-ink-4">本院尚无医保局定向发布的数据与待办</div></div>
            <div v-if="!data.noOwnData" class="rounded-2xl bg-[linear-gradient(135deg,var(--brand),color-mix(in_srgb,var(--brand)_85%,#fff))] p-4 text-white">
              <div class="text-xs opacity-85">{{ data.home.deviation.label }}</div>
              <div class="yb-num text-[34px] font-semibold">{{ data.home.deviation.value }}<span class="text-sm"> {{ data.home.deviation.unit }}</span></div>
              <div class="text-xs opacity-85">{{ data.home.deviation.note }}</div>
            </div>
            <div v-if="!data.noOwnData" class="grid grid-cols-2 gap-2.5">
              <div v-for="k in data.home.kpis" :key="k.label" class="rounded-[14px] bg-white p-3">
                <div class="text-[11px] text-ink-4">{{ k.label }}</div>
                <div :class="cn('yb-num text-[22px] font-semibold', k.valueTone === 'bad' && 'text-bad')">{{ k.value }}</div>
                <div :class="cn('text-[11px]', k.subTone === 'brand' ? 'text-brand' : 'text-warn-ink')">{{ k.sub }}</div>
              </div>
            </div>
            <div v-if="!data.noOwnData" class="rounded-[14px] bg-white px-3.5 py-1">
              <div class="pt-2.5 pb-1 text-xs text-ink-4">待办 · {{ pend }}</div>
              <div v-press
                v-for="t in todos"
                :key="t.id"
                class="flex min-h-12 cursor-pointer items-center gap-2.5 border-t border-line-3 py-3"
                @click="open(t.target)"
              >
                <span class="size-2 shrink-0 rounded-full" :style="{ background: t.c }" />
                <span :class="cn('flex-1 text-sm', t.isDone ? 'text-ink-5' : 'text-ink-1')">{{ t.label }}</span>
                <span class="text-[11px] whitespace-nowrap text-ok-ink">{{ t.isDone ? t.doneLabel : '' }}</span>
                <span class="text-ink-5">›</span>
              </div>
            </div>
          </template>

          <!-- report -->
          <template v-if="screen === 'report'">
            <div class="flex flex-col gap-2 rounded-2xl bg-white p-4">
              <div class="text-xs text-ink-4">{{ data.report.meta }}</div>
              <div class="text-lg font-semibold">{{ data.report.title }}</div>
              <div class="flex gap-1.5">
                <span class="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] text-brand">{{ data.report.pages }}</span>
                <span v-if="reportOut" :class="cn('rounded-full px-2 py-0.5 text-[11px]', signed ? 'bg-ok-soft text-ok-ink' : 'bg-warn-soft text-warn-ink')">
                  {{ signed ? '已签收' + (data.report.signedAt ? ' · ' + data.report.signedAt : '') : data.report.pendingLabel }}
                </span>
                <span v-else class="rounded-full bg-surface-3 px-2 py-0.5 text-[11px] text-ink-4">尚未发布</span>
              </div>
              <div v-if="!reportOut" class="text-xs leading-[1.7] text-ink-4">本期报告尚在医保局审批中,批准发布后即可查阅与签收。</div>
              <div v-else-if="!signed && !canSign" class="text-xs leading-[1.7] text-ink-4">签收须由机构本院身份完成,当前身份仅可查看。</div>
            </div>
            <div v-for="x in reportOut ? data.report.sections : []" :key="x.n" class="rounded-[14px] bg-white px-4 py-3.5">
              <div class="text-xs font-semibold text-brand">{{ x.n }}</div>
              <div class="mt-1 text-[13px] leading-[1.7] text-ink-2">{{ x.t }}</div>
            </div>
          </template>

          <!-- alert -->
          <template v-if="screen === 'alert'">
            <div class="flex flex-col gap-2.5 rounded-2xl bg-white p-4">
              <div class="flex items-center gap-2">
                <span class="rounded bg-bad-soft px-2 py-px text-[11px] font-bold text-bad-ink">{{ data.alert.level }}</span>
                <span class="text-xs text-ink-4">{{ data.alert.meta }}</span>
              </div>
              <div class="text-[17px] font-semibold">{{ data.alert.title }}</div>
              <div class="flex items-baseline gap-2 whitespace-nowrap">
                <span class="yb-num text-[34px] font-semibold text-bad">{{ data.alert.value }}</span>
                <span class="text-[13px] text-ink-4">{{ data.alert.unitNote }}</span>
              </div>
              <div class="flex h-16 items-end gap-1 border-b border-line-2">
                <span v-for="(x, i) in bars" :key="i" class="flex-1 rounded-t-[2px]" :style="{ height: x.h, background: x.c }" />
              </div>
            </div>
            <div class="rounded-2xl bg-white px-4 py-3.5 text-[13px] leading-[1.7] text-ink-2">
              <div class="mb-1 text-xs text-ink-4">归因提示</div>{{ data.alert.attribution }}
            </div>
            <div :class="cn('rounded-xl px-3.5 py-3 text-xs', rcDone ? 'bg-ok-soft text-ok-ink' : 'bg-warn-soft text-[#7A4510]')">
              {{ rcDone ? data.alert.doneNote : data.alert.pendingNote }}
            </div>
          </template>

          <!-- receipt form -->
          <template v-if="screen === 'rcpt'">
            <div class="px-1 text-xs text-ink-4">原因类别</div>
            <div class="flex flex-wrap gap-2">
              <button type="button"
                v-for="(l, i) in data.receiptCategories"
                :key="l"
                :class="cn(
                  'flex min-h-10 cursor-pointer items-center rounded-full border px-3.5 text-[13px]',
                  i === cat ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
                )"
                @click="cat = i"
              >{{ l }}</button>
            </div>
            <div class="px-1 pt-1.5 text-xs text-ink-4">说明</div>
            <Textarea
              v-model="txt"
              placeholder="简述原因与整改措施"
              class="h-[140px] min-h-[140px] shrink-0 resize-none rounded-xl border-line-1 bg-white p-3 text-sm shadow-none [field-sizing:fixed] focus-visible:ring-0 md:text-sm"
            />
            <div class="flex min-h-11 items-center gap-2 rounded-xl border border-dashed border-[#C9D3E1] bg-white px-3 py-2.5 text-[13px] text-ink-3">
              📎 附件(可选)· 手术记录 / 耗材清单
            </div>
          </template>

          <!-- done -->
          <div v-if="screen === 'done'" class="flex flex-1 flex-col items-center justify-center gap-3 px-2.5 py-10 text-center">
            <div class="flex size-[72px] items-center justify-center rounded-full bg-ok-soft text-4xl text-ok">✓</div>
            <div class="text-xl font-semibold">{{ doneT }}</div>
            <div class="text-[13px] leading-[1.7] whitespace-pre-line text-ink-4">{{ doneS }}</div>
          </div>

          <!-- messages -->
          <template v-if="screen === 'msgs'">
            <div class="px-1 pt-0.5 pb-1 text-[22px] font-semibold">消息</div>
            <div class="rounded-[14px] bg-white px-3.5 py-1">
              <div v-press
                v-for="m in messages"
                :key="m.label"
                class="flex min-h-12 cursor-pointer items-start gap-2.5 border-t border-line-3 py-3"
                @click="openMsg(m)"
              >
                <span class="mt-[7px] size-2 shrink-0 rounded-full" :style="{ background: m.c }" />
                <div class="min-w-0 flex-1">
                  <div class="text-sm">{{ m.label }}</div>
                  <div class="text-[11px] text-ink-5">{{ m.t }}</div>
                </div>
              </div>
            </div>
          </template>

          <!-- me -->
          <template v-if="screen === 'me'">
            <div class="flex items-center gap-3 rounded-2xl bg-white p-4">
              <span class="flex size-12 items-center justify-center rounded-full bg-brand-soft text-lg font-semibold text-brand">{{ data.me.name[0] }}</span>
              <div>
                <div class="text-base font-semibold">{{ data.me.name }} · {{ data.me.title }}</div>
                <div class="text-xs text-ink-4">{{ data.me.org }}</div>
              </div>
            </div>
            <div class="rounded-[14px] bg-white px-3.5 py-1">
              <div v-for="r in meRows" :key="r.k" class="flex min-h-12 items-center gap-2.5 border-t border-line-3 py-3">
                <span class="flex-1 text-sm">{{ r.k }}</span>
                <span class="text-[13px] text-ink-4">{{ r.v }}</span>
              </div>
            </div>
          </template>
        </div>

        <!-- bottom CTA (thumb zone) -->
        <div v-if="cta" class="flex shrink-0 flex-col gap-2 border-t border-line-2 bg-white px-3.5 pt-2.5 pb-3.5">
          <Button
            :class="cn('h-12 rounded-xl text-[15px] hover:brightness-100', cta.muted && 'bg-ink-5')"
            @click="cta.fn"
          >{{ cta.t }}</Button>
          <Button
            v-if="screen === 'alert' && !rcDone"
            variant="outline"
            class="h-11 rounded-xl text-sm font-normal text-ink-3 hover:brightness-100"
            :disabled="reviewAsked || busy"
            @click="requestReview"
          >{{ reviewAsked ? '已申请复核' : '申请复核' }}</Button>
        </div>

        <!-- tab bar -->
        <div v-if="showTabs" class="grid h-[72px] shrink-0 grid-cols-4 border-t border-line-1 bg-white">
          <div v-press
            v-for="[l, k] in TABS"
            :key="k"
            :class="cn(
              'relative flex cursor-pointer flex-col items-center gap-[3px] pt-2.5 text-[11px]',
              screen === k ? 'font-semibold text-brand' : 'font-normal text-ink-4',
            )"
            @click="go(k)"
          >
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path :d="IPS[k]" /></svg>
            {{ l }}
            <span
              v-if="k === 'msgs' && unread > 0"
              class="absolute top-2 left-[calc(50%+8px)] flex h-4 min-w-4 items-center justify-center rounded-lg bg-bad px-1 text-[10px] font-normal text-white"
            >{{ unread }}</span>
          </div>
        </div>

        <!-- phone toast -->
        <div
          v-if="mToast"
          class="absolute top-1/2 left-1/2 -translate-1/2 rounded-xl bg-[rgba(11,21,38,.88)] px-[18px] py-3 text-[13px] whitespace-nowrap text-white"
        >{{ mToast }}</div>
      </div>
    </div>
  </section>
</template>
