<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { Tone } from '@/api/types'
import { portalReportsApi, type CheckItem, type CheckRound, type MyOpinion, type OpinionRef } from '@/api/portalReports'
import Chip from '@/components/shared/Chip.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/**
 * B5 我的意见与机构核对：核对期倒计时（每秒刷新，截止后视为确认）+ 逐项确认 / 有异议；
 * 提交意见必须关联具体指标或报告段落、说明必填（前后端都校验）；查看答复并五星评价（流程 1）。
 */
const page = pageDef('B5')!
const route = useRoute()

const round = ref<CheckRound | null>(null)
const refs = ref<OpinionRef[]>([])
const cats = ref<string[]>([])
const mine = ref<MyOpinion[]>([])

const refId = ref<number | null>(null)
const cat = ref('数据异议')
const text = ref('')
const checkItem = ref<number | null>(null)
const busy = ref(false)
const formEl = ref<HTMLElement | null>(null)

// ---- 倒计时：以服务端时间校正本机时钟，每秒刷新
const offset = ref(0)
const now = ref(Date.now())
useIntervalFn(() => {
  now.value = Date.now()
  if (round.value && !round.value.closed && remainMs.value <= 0) void loadRound()
}, 1000)
const remainMs = computed(() => (round.value ? Math.max(0, new Date(round.value.deadline).getTime() - (now.value + offset.value)) : 0))
const countdown = computed(() => {
  let ms = remainMs.value
  const d = Math.floor(ms / 864e5)
  ms %= 864e5
  const h = Math.floor(ms / 36e5)
  ms %= 36e5
  const m = Math.floor(ms / 6e4)
  const s = Math.floor((ms % 6e4) / 1000)
  const p = (n: number) => String(n).padStart(2, '0')
  return (d ? `${d} 天 ` : '') + `${p(h)}:${p(m)}:${p(s)}`
})
const closed = computed(() => !!round.value && (round.value.closed || remainMs.value <= 0))

function setRound(r: CheckRound | null) {
  round.value = r
  if (r) offset.value = r.serverNow - Date.now()
}
async function loadRound() {
  setRound((await portalReportsApi.check()).round)
}
async function loadOpinions() {
  const r = await portalReportsApi.opinions()
  refs.value = r.refs
  cats.value = r.categories
  mine.value = r.rows
}

onMounted(async () => {
  try {
    await Promise.all([loadRound(), loadOpinions()])
    // 从 B4「对本报告提意见」进入：预选该报告的第一个段落
    const rid = Number(route.query.report)
    if (rid) refId.value = refs.value.find((x) => x.reportId === rid)?.id ?? null
  } catch (e) {
    notifyError(e)
  }
})

// ---- 核对项
function itemNote(c: CheckItem): [string, string] {
  if (c.state === 'OK') return ['✓ 已确认', 'text-success']
  if (c.state === 'AUTO') return ['逾期视为确认', 'text-ink-muted']
  return [c.ticketNo ? `已提异议,转意见工单 ${c.ticketNo}` : '已提异议,请在下方补充说明', 'text-danger']
}

async function decide(c: CheckItem, d: 'OK' | 'OBJECT') {
  try {
    setRound(await portalReportsApi.decide(c.idx, d))
    if (d === 'OBJECT') {
      refId.value = refs.value.find((x) => x.reportId === round.value?.reportId)?.id ?? refId.value
      cat.value = '数据异议'
      checkItem.value = c.idx
      notify('请在下方补充异议说明')
      await nextTick()
      formEl.value?.querySelector('textarea')?.focus()
    } else notify(`已确认:${c.label}`)
  } catch (e) {
    notifyError(e)
    await loadRound().catch(() => {})
  }
}

const checkLabel = computed(() => round.value?.items.find((x) => x.idx === checkItem.value)?.label)
const selRef = computed(() => refs.value.find((x) => x.id === refId.value))
const isCheckPeriod = computed(() => !closed.value && !!round.value && (checkItem.value != null || (!!selRef.value?.reportId && selRef.value.reportId === round.value.reportId)))

// ---- 提交意见（前端校验与服务端一致：关联对象必选、说明必填）
const hint = computed(() => (refId.value == null ? '必须关联具体指标或报告段落' : !text.value.trim() ? '请填写说明' : ''))
const ok = computed(() => !hint.value)

async function submit() {
  if (!ok.value || busy.value) return
  busy.value = true
  try {
    const r = await portalReportsApi.submitOpinion({ refId: refId.value, category: cat.value, text: text.value, checkItem: checkItem.value })
    notify(r.message)
    text.value = ''
    refId.value = null
    checkItem.value = null
    await Promise.all([loadOpinions(), loadRound()])
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

// ---- 我的意见
const OST: Record<MyOpinion['status'], [string, Tone]> = { WAIT: ['待答复', 'warning'], DOING: ['处理中', 'primary'], DONE: ['已答复', 'success'] }

async function rate(o: MyOpinion, n: number) {
  try {
    const r = await portalReportsApi.rate(o.no, n)
    mine.value = mine.value.map((x) => (x.no === r.no ? r : x))
    notify('感谢评价')
  } catch (e) {
    notifyError(e)
  }
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />

    <div class="mt-5 flex flex-col gap-3">
      <!-- 核对卡（紫边） -->
      <section v-if="round" class="rounded-[10px] border border-ai-line bg-surface px-[18px] py-4" data-testid="check-card">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <span class="text-[14px] font-semibold text-ink">机构核对 · {{ round.title }}</span>
          <span v-if="!closed" class="rounded-md bg-ai-soft px-2.5 py-0.5 text-[12px] text-ai-ink">
            核对剩余 <b class="font-mono font-semibold" data-testid="countdown">{{ countdown }}</b>
          </span>
          <span v-else class="rounded-md bg-chip px-2.5 py-0.5 text-[12px] text-ink-muted" data-testid="countdown">核对期已截止 · 未处理项视为确认</span>
          <span class="ml-auto text-[12px] text-ink-faint">截止 {{ round.deadlineLabel }} · 逾期视为确认</span>
        </div>
        <div class="grid grid-cols-4 gap-2.5">
          <div v-for="c in round.items" :key="c.idx" class="rounded-lg border border-line px-3 py-2.5" :data-check="c.idx" :data-state="c.state">
            <div class="text-[11px] text-ink-muted">{{ c.label }}</div>
            <div class="text-[16px] font-semibold text-ink">{{ c.value }}</div>
            <div class="mt-1.5 flex gap-2.5 text-[12px]">
              <template v-if="c.state === 'OPEN' && !closed">
                <button type="button" class="cursor-pointer text-primary hover:underline" data-act="ok" @click="decide(c, 'OK')">确认</button>
                <button type="button" class="cursor-pointer text-danger hover:underline" data-act="object" @click="decide(c, 'OBJECT')">有异议</button>
              </template>
              <span v-else-if="c.state === 'OPEN'" class="text-ink-muted">逾期视为确认</span>
              <span v-else :class="itemNote(c)[1]">{{ itemNote(c)[0] }}</span>
            </div>
          </div>
        </div>
      </section>

      <div class="grid grid-cols-2 items-start gap-3">
        <!-- 提交意见 -->
        <Panel title="提交意见" data-testid="opinion-form">
          <div ref="formEl" class="flex flex-col gap-3 text-[12px]">
            <div>
              <div class="mb-1.5 text-ink-muted">关联指标或报告段落 <span class="text-danger">*</span></div>
              <div class="flex flex-wrap gap-1.5" data-testid="refs">
                <Chip v-for="r in refs" :key="r.id" :on="r.id === refId" :data-ref="r.label" @click="refId = r.id">{{ r.label }}</Chip>
              </div>
            </div>
            <div>
              <div class="mb-1.5 text-ink-muted">类别</div>
              <div class="flex gap-1.5" data-testid="cats">
                <Chip v-for="c in cats" :key="c" :on="c === cat" @click="cat = c">{{ c }}</Chip>
              </div>
            </div>
            <div v-if="checkLabel" class="flex items-center gap-2 rounded-lg border border-ai-line bg-ai-soft px-2.5 py-1.5 text-ai-ink">
              关联核对项:{{ checkLabel }}
              <button type="button" class="ml-auto cursor-pointer text-ai-muted hover:text-ai-ink" aria-label="取消关联核对项" @click="checkItem = null">×</button>
            </div>
            <div v-else-if="isCheckPeriod" class="rounded-lg border border-ai-line bg-ai-soft px-2.5 py-1.5 text-ai-ink">核对期内针对核对稿的意见按「核对期异议」处理,须在截止前答复</div>
            <Textarea v-model="text" placeholder="说明异议内容与依据" class="h-[90px] resize-none text-[12px]" maxlength="1000" data-testid="opinion-text" />
            <div class="flex items-center gap-2.5">
              <span :class="ok ? '' : 'cursor-not-allowed'">
                <Button size="sm" :disabled="!ok || busy" data-testid="opinion-submit" @click="submit">提交</Button>
              </span>
              <span class="text-warning" data-testid="opinion-hint">{{ hint }}</span>
            </div>
          </div>
        </Panel>

        <!-- 我的意见 -->
        <Panel title="我的意见" data-testid="my-opinions">
          <div v-if="!mine.length" class="py-6 text-center text-[12px] text-ink-faint">暂无意见</div>
          <div v-for="o in mine" :key="o.no" class="border-b border-divider py-2.5 text-[12px] last:border-b-0" :data-opinion="o.no">
            <div class="flex items-center justify-between gap-2">
              <span class="text-primary">{{ o.ref }}</span>
              <Tag :tone="OST[o.status][1]">{{ OST[o.status][0] }}</Tag>
            </div>
            <div class="mt-1 text-ink">{{ o.content }}</div>
            <div class="mt-1 text-[11px] text-ink-faint">
              <span class="font-mono">{{ o.no }}</span> · {{ o.category }} · 提交于 {{ o.createdAt }}<template v-if="o.dueLabel"> · {{ o.dueLabel }}</template>
            </div>
            <template v-if="o.status === 'DONE'">
              <div class="mt-1.5 rounded-lg bg-subtle px-2.5 py-2"><span class="text-ink-muted">医保局答复:</span>{{ o.reply }}</div>
              <div class="mt-1.5 flex items-center gap-1" data-testid="stars">
                <span class="mr-1 text-ink-muted">评价</span>
                <button
                  v-for="n in 5"
                  :key="n"
                  type="button"
                  class="cursor-pointer px-0.5 text-[15px] leading-none"
                  :class="n <= (o.rating ?? 0) ? 'text-chart-amber' : 'text-neutral'"
                  :aria-label="`${n} 星`"
                  :data-star="n"
                  @click="rate(o, n)"
                >★</button>
              </div>
            </template>
          </div>
        </Panel>
      </div>
    </div>
  </div>
</template>
