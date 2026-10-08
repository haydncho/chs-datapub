<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getJson, runAction, usePageData } from '@/api/client'
import { goPage } from '@/app/router'
import { say } from '@/app/shell'
import { session } from '@/app/session'
import { Button } from '@/components/ui/button'
import { pad } from '@/lib/format'
import { A7_SEED, type A7Data, type A7Section, type A7Topic, type A7TrackStep, type A7Version } from '@/mock/A7'
import TopicHeader from './A7/TopicHeader.vue'
import SectionOutline from './A7/SectionOutline.vue'
import SectionCard from './A7/SectionCard.vue'
import OverviewChart from './A7/OverviewChart.vue'
import StructureChart from './A7/StructureChart.vue'
import BehaviourChart from './A7/BehaviourChart.vue'
import WaterfallChart from './A7/WaterfallChart.vue'
import DumbbellChart from './A7/DumbbellChart.vue'
import OptimisationChart from './A7/OptimisationChart.vue'
import AdviceBlock from './A7/AdviceBlock.vue'
import SidePanel, { type A7Tab, type CommentView } from './A7/SidePanel.vue'
import ExportDialog from './A7/ExportDialog.vue'
import { commentsCsv, downloadText } from './A7/commentsCsv'

const data = usePageData('A7', A7_SEED)
const route = useRoute()

/** re-read the read model after an accepted action: reviews / task steps are server state */
async function refresh() {
  try {
    const remote = await getJson<A7Data>('/pages/A7')
    if (remote && typeof remote === 'object') data.value = { ...A7_SEED, ...remote }
  } catch { /* keep the current view */ }
}

// ---- which topic: #/A7?topic=<A6 id>; default = the seeded manuscript (BR25) ----
const topicId = computed(() => (typeof route.query.topic === 'string' && route.query.topic) || data.value.topicId)
const isSeed = computed(() => topicId.value === data.value.topicId)
const ref6 = computed(() => data.value.topics?.[topicId.value])
const known = computed(() => isSeed.value || !!ref6.value)
const review = computed(() => data.value.reviews?.[topicId.value])

const topic = computed<A7Topic>(() => {
  const r = ref6.value
  if (isSeed.value) {
    return r ? { ...data.value.topic, recommendTag: `选题推荐 · 得分 ${r.score} · 已采纳` } : data.value.topic
  }
  const code = r?.code ?? topicId.value.replace(/^T-/, '')
  const title = r?.title ?? topicId.value
  return {
    code,
    name: (title.startsWith(code) ? title.slice(code.length).trim() : title) + ' · 专题',
    recommendTag: r ? `选题推荐 · 得分 ${r.score} · 已采纳` : '未采纳',
    envTag: data.value.topic.envTag,
    meta: r ? `${r.kind} · ` + r.facts.map(f => `${f.k} ${f.v}`).join(' · ') : '',
  }
})

/** other topics: the seven-section skeleton, §01 drafted from the A6 facts, the rest awaiting analysis */
const sections = computed<A7Section[]>(() => {
  if (isSeed.value) return data.value.sections
  const r = ref6.value
  const facts = r?.facts ?? []
  return data.value.sections.map((s, i) => {
    if (i === 0 && facts.length) {
      const draft: string[] = [`${r!.title}:`]
      facts.forEach((f, j) => draft.push(`${j ? ';' : ''}${f.k} `, f.v))
      draft.push('。详细归因待受控分析环境完成后补充。')
      return { name: s.name, draft, sources: facts.map(f => ({ value: f.v, source: `${f.k} · A6 选题推荐(实时计算批次)` })) }
    }
    return { name: s.name, draft: [`本段大模型初稿待生成:请在受控分析环境完成「${s.name}」分析后点击“重新生成”,审定前不对外引用。`], sources: [] }
  })
})
const seedComments = computed(() => (isSeed.value ? data.value.comments : []))

// ---- review state (server) ----------------------------------------------
const approved = computed(() => new Set(review.value?.approved ?? (isSeed.value ? data.value.approved : [])))
const resolved = computed(() => new Set(
  review.value?.resolved ?? seedComments.value.flatMap((c, k) => (c.resolved ? [k] : [])),
))
const pushed = computed<Record<string, string>>(() => {
  const out: Record<string, string> = { ...(review.value?.pushed ?? {}) }
  seedComments.value.forEach((c, k) => { if (c.pushedAs && !out[k]) out[k] = c.pushedAs })
  return out
})
const submitted = computed(() => !!review.value?.submitted)
const busy = ref(false)

const act = ref(0)
const tab = ref<A7Tab>('批注')
const expOpen = ref(false)

const total = computed(() => sections.value.length)
const nOk = computed(() => approved.value.size)

const secLabel = (i: number) => `${pad(i + 1)} ${sections.value[i]?.name ?? ''}`

const comments = computed<CommentView[]>(() =>
  seedComments.value.map((c, k) => ({
    k,
    section: c.section,
    sec: secLabel(c.section),
    who: c.who,
    role: c.role,
    text: c.text,
    time: c.time,
    resolved: resolved.value.has(k),
    pushedAs: pushed.value[k],
  })),
)
const unresolved = computed(() => comments.value.filter(c => !c.resolved).length)
/** not handled and not yet pushed to 意见与申诉 */
const pending = computed(() => comments.value.filter(c => !c.resolved && !c.pushedAs).length)

const outline = computed(() =>
  sections.value.map((s, i) => ({
    name: s.name,
    ok: approved.value.has(i),
    comments: comments.value.filter(c => c.section === i && !c.resolved).length,
  })),
)

/** 数据核查: every data-bound number of the active section, traced to its own source */
const checks = computed(() => sections.value[act.value]?.sources ?? [])

const versions = computed<A7Version[]>(() => {
  const base: A7Version[] = isSeed.value
    ? data.value.versions
    : [{ title: 'v1 · 大模型初稿', meta: '采纳后生成 · 系统', current: true }]
  const added = review.value?.versions ?? []
  return [...added, ...base].map((v, i) => ({ ...v, current: i === 0 }))
})

/** progress follows the topic's A8 task (分析成稿 3 → 专家组审核 4 → 召集人审批 5 → 定向发布 6+) */
const step = computed(() => data.value.taskSteps?.[topicId.value] ?? (submitted.value ? 4 : 3))
const track = computed<A7TrackStep[]>(() =>
  data.value.track.map((t, i) => {
    const s = t.a8Step ?? 0
    const state = i === 0 ? 'done' : step.value > s ? 'done' : step.value === s ? 'cur' : 'todo'
    return { ...t, state, sub: i === 0 && !isSeed.value ? '' : t.sub }
  }),
)

const role = computed(() => session.current?.identity.role ?? null)
/** 受控分析环境:委托分析只能导出审核后的聚合结果,意见单(具名明细)不能导出 */
const canExport = computed(() => role.value !== 'analyst')

// ---- scroll-to-section --------------------------------------------------
const secEls: (HTMLElement | null)[] = []
const setSecEl = (i: number) => (el: unknown) => {
  secEls[i] = (el as { $el?: HTMLElement } | null)?.$el ?? (el as HTMLElement | null)
}
/** sticky shell header (60) + topic header (136) + breathing room */
const STICKY_OFFSET = 208
/** 窄屏:专题头不吸顶,只有壳层 60 + 章节条约 64 */
const STICKY_OFFSET_NARROW = 144
function goSec(i: number) {
  act.value = i
  const el = secEls[i]
  const off = window.innerWidth >= 1280 ? STICKY_OFFSET : STICKY_OFFSET_NARROW
  if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - off, behavior: 'smooth' })
}

// ---- actions: the view changes only after the server accepted them -------
async function run<T = unknown>(action: string, payload: Record<string, unknown>) {
  if (busy.value) return null
  busy.value = true
  try {
    const r = await runAction<{ result?: T }>('A7', action, { topic: topicId.value, ...payload })
    if (!r.ok) {
      say(r.error)
      return null
    }
    await refresh()
    return r.data?.result ?? ({} as T)
  } finally {
    busy.value = false
  }
}
async function approve(i: number) {
  act.value = i
  if (await run('approveSection', { section: i })) say(`第 ${i + 1} 段已审定`)
}
async function regen(i: number) {
  const res = await run<{ reset: boolean }>('regenerateSection', { section: i })
  if (res) say(res.reset ? `第 ${i + 1} 段已基于最新批次重新生成 · 原审定已撤销,请重新审定` : `第 ${i + 1} 段已基于最新批次重新生成初稿`)
}
async function resolve(k: number) {
  if (await run('resolveComment', { comment: k })) say('批注已标记为已处理')
}
async function submit() {
  if (submitted.value) {
    say('已提交,机构核对与专家组审核进行中')
    return
  }
  if (nOk.value < total.value) {
    say(`还有 ${total.value - nOk.value} 段未审定`)
    return
  }
  const res = await run('submitReview', { approved: [...approved.value].sort((a, b) => a - b) })
  if (res) say(isSeed.value ? '已提交:机构核对(18 家)与专家组审核同步开始' : '已提交:机构核对与专家组审核同步开始')
}
async function exportExcel() {
  if (!canExport.value) {
    say('受控分析环境仅可导出审核后的聚合结果,意见单请由行政管理组导出')
    return
  }
  const e = data.value.export
  const res = await run<{ traceNo: string; watermark: string }>('exportCommentsExcel', { docNo: e.docNo, fileName: e.fileName })
  if (!res) return
  const status = (c: CommentView) => (c.resolved ? '已处理' : c.pushedAs ? `已推送 ${c.pushedAs}` : '待处理')
  const csv = commentsCsv(
    comments.value.map(c => ({ sec: c.sec, who: c.who, role: c.role, text: c.text, time: c.time, status: status(c) })),
    { title: `${topic.value.code} 专题意见单 · ${e.subtitle}`, docNo: e.docNo, traceNo: res.traceNo, watermark: res.watermark },
  )
  downloadText(e.fileName, csv)
  say(`已下载 ${e.fileName} · 水印 ${res.traceNo}`)
}
async function pushComments() {
  const e = data.value.export
  const res = await run<{ count: number; ids: string[] }>('pushComments', { docNo: e.docNo })
  if (!res) return
  expOpen.value = false
  say(`已推送 ${res.count} 条至意见与申诉 · ${res.ids.join('、')}`)
}
</script>

<template>
  <section data-screen-label="A7 病种专题工作台" class="flex flex-col max-xl:[&_.text-\[10px\]]:text-[11px] max-xl:[&_.text-\[11px\]]:text-xs">
    <div v-if="!known" class="m-6 flex flex-col items-center gap-2 rounded-xl border border-dashed border-line-4 bg-white px-5 py-12 text-ink-4">
      <div class="font-semibold text-ink-2">该选题尚未采纳,不能进入专题工作台</div>
      <div class="text-xs">选题编号 {{ topicId }} · 请先在智能推荐中由行政管理组采纳</div>
      <div class="mt-2 flex gap-2">
        <Button variant="outline" class="font-normal" @click="goPage('A6')">返回智能推荐</Button>
        <Button @click="goPage('A7')">打开 {{ data.topic.code }} 专题</Button>
      </div>
    </div>

    <template v-else>
      <TopicHeader
        :topic="topic"
        :collaborators="data.collaborators"
        :track="track"
        :n-ok="nOk"
        :total="total"
        :submitted="submitted"
        :busy="busy"
        @submit="submit"
      />

      <div class="grid grid-cols-1 items-start lg:max-xl:grid-cols-[minmax(0,1fr)_320px] xl:grid-cols-[240px_minmax(0,1fr)_340px]">
        <SectionOutline class="lg:max-xl:col-span-2" :items="outline" :active="act" @go="goSec" />

        <main class="flex min-w-0 justify-center px-6 pt-5 pb-20 max-xl:px-5 max-xl:pb-6 max-sm:px-4">
          <div class="flex w-full max-w-[800px] flex-col gap-3 rounded-[14px] border border-line-1 bg-white px-11 pt-9 pb-12 max-xl:px-8 max-xl:pt-6 max-xl:pb-8 lg:max-xl:px-7 max-sm:px-5 shadow-[0_1px_3px_rgba(15,23,42,.04)]">
            <div class="text-[11px] text-ink-5">{{ isSeed ? data.docMeta : `示例市医保数据工作组 · ${topic.code} 专题 · 初稿` }}</div>
            <div v-if="submitted" class="rounded-[10px] bg-ok-soft px-3.5 py-2.5 text-xs text-ok-ink">
              ✓ 已于 {{ review?.submittedAt }} 提交核对与审核({{ review?.submittedBy }}) · 稿件已锁定,机构核对与专家组审核进行中
            </div>
            <SectionCard
              v-for="(sec, i) in sections"
              :key="topicId + i"
              :ref="setSecEl(i)"
              :index="i"
              :name="sec.name"
              :draft="sec.draft"
              :ok="approved.has(i)"
              :active="act === i"
              :locked="submitted || busy"
              @focus="act = i"
              @approve="approve(i)"
              @regen="regen(i)"
            >
              <template v-if="isSeed">
                <OverviewChart v-if="i === 0" v-bind="data.overview" />
                <StructureChart v-else-if="i === 1" v-bind="data.structure" />
                <BehaviourChart v-else-if="i === 2" v-bind="data.behaviour" />
                <WaterfallChart v-else-if="i === 3" v-bind="data.waterfall" />
                <DumbbellChart v-else-if="i === 4" v-bind="data.dumbbell" />
                <OptimisationChart v-else-if="i === 5" v-bind="data.optimisation" />
                <AdviceBlock v-else-if="i === 6" :advice="data.advice" />
              </template>
            </SectionCard>
          </div>
        </main>

        <SidePanel
          v-model:tab="tab"
          :comments="comments"
          :active="act"
          :active-name="secLabel(act)"
          :checks="checks"
          :check-note="data.checkNote"
          :versions="versions"
          :pending="pending"
          :unresolved="unresolved"
          @resolve="resolve"
          @export="expOpen = true"
        />
      </div>

      <ExportDialog
        v-model:open="expOpen"
        :rows="comments"
        :pending="pending"
        :doc-no="data.export.docNo"
        :subtitle="isSeed ? data.export.subtitle : `${topic.code} 专题 · 初稿`"
        :title="data.export.title"
        :foot-note="data.export.footNote"
        :can-export="canExport"
        :busy="busy"
        @excel="exportExcel"
        @push="pushComments"
      />
    </template>
  </section>
</template>
