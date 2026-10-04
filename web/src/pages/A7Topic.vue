<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { pad } from '@/lib/format'
import { A7_SEED } from '@/mock/A7'
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

const data = usePageData('A7', A7_SEED)

// ---- review state -------------------------------------------------------
const approved = ref<Set<number>>(new Set())
const resolved = ref<Set<number>>(new Set())
watch(
  data,
  d => {
    approved.value = new Set(d.approved)
    resolved.value = new Set(d.comments.flatMap((c, k) => (c.resolved ? [k] : [])))
  },
  { immediate: true },
)

const act = ref(0)
const tab = ref<A7Tab>('批注')
const expOpen = ref(false)

const total = computed(() => data.value.sections.length)
const nOk = computed(() => approved.value.size)

const secLabel = (i: number) => `${pad(i + 1)} ${data.value.sections[i]?.name ?? ''}`

const comments = computed<CommentView[]>(() =>
  data.value.comments.map((c, k) => ({
    k,
    section: c.section,
    sec: secLabel(c.section),
    who: c.who,
    role: c.role,
    text: c.text,
    time: c.time,
    resolved: resolved.value.has(k),
  })),
)
const pending = computed(() => comments.value.filter(c => !c.resolved).length)

const outline = computed(() =>
  data.value.sections.map((s, i) => ({
    name: s.name,
    ok: approved.value.has(i),
    comments: comments.value.filter(c => c.section === i && !c.resolved).length,
  })),
)

/** 数据核查: §01 has full lineage; other sections trace their first two numbers. */
const checks = computed(() => {
  const d = data.value
  if (act.value === 0) return d.sources
  const draft = d.sections[act.value]?.draft ?? []
  return [draft[1], draft[3]]
    .map((v, j) => ({ value: v ?? '—', source: d.checkSources[j] ?? '' }))
    .filter(c => c.value !== '—')
})

// ---- scroll-to-section --------------------------------------------------
const secEls: (HTMLElement | null)[] = []
const setSecEl = (i: number) => (el: unknown) => {
  secEls[i] = (el as { $el?: HTMLElement } | null)?.$el ?? (el as HTMLElement | null)
}
/** sticky shell header (60) + topic header (136) + breathing room */
const STICKY_OFFSET = 208
function goSec(i: number) {
  act.value = i
  const el = secEls[i]
  if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - STICKY_OFFSET, behavior: 'smooth' })
}

// ---- actions ------------------------------------------------------------
function approve(i: number) {
  approved.value = new Set(approved.value).add(i)
  act.value = i
  say(`第 ${i + 1} 段已审定`)
  sendAction('A7', 'approveSection', { section: i })
}
function regen(i: number) {
  say('已基于最新批次重新生成初稿 · 原稿保留为 v3')
  sendAction('A7', 'regenerateSection', { section: i })
}
function resolve(k: number) {
  resolved.value = new Set(resolved.value).add(k)
  sendAction('A7', 'resolveComment', { comment: k })
}
function submit() {
  if (nOk.value < total.value) {
    say(`还有 ${total.value - nOk.value} 段未审定`)
    return
  }
  say('已提交:机构核对(18 家)与专家组审核同步开始')
  sendAction('A7', 'submitReview', { approved: [...approved.value].sort((a, b) => a - b) })
}
function exportExcel() {
  const e = data.value.export
  say(`已生成 ${e.fileName} · 带水印`)
  sendAction('A7', 'exportCommentsExcel', { docNo: e.docNo, fileName: e.fileName })
}
function pushComments() {
  const e = data.value.export
  expOpen.value = false
  say(`已推送 ${pending.value} 条至意见与申诉 · 编号 ${e.docNo}`)
  sendAction('A7', 'pushComments', {
    docNo: e.docNo,
    count: pending.value,
    comments: comments.value.filter(c => !c.resolved).map(c => c.k),
  })
}
</script>

<template>
  <section data-screen-label="A7 病种专题工作台" class="flex flex-col">
    <TopicHeader
      :topic="data.topic"
      :collaborators="data.collaborators"
      :track="data.track"
      :n-ok="nOk"
      :total="total"
      @submit="submit"
    />

    <div class="grid grid-cols-[240px_minmax(0,1fr)_340px] items-start">
      <SectionOutline :items="outline" :active="act" @go="goSec" />

      <main class="flex min-w-0 justify-center px-6 pt-5 pb-20">
        <div class="flex w-full max-w-[800px] flex-col gap-3 rounded-[14px] border border-line-1 bg-white px-11 pt-9 pb-12 shadow-[0_1px_3px_rgba(15,23,42,.04)]">
          <div class="text-[11px] text-ink-5">{{ data.docMeta }}</div>
          <SectionCard
            v-for="(sec, i) in data.sections"
            :key="i"
            :ref="setSecEl(i)"
            :index="i"
            :name="sec.name"
            :draft="sec.draft"
            :ok="approved.has(i)"
            :active="act === i"
            @focus="act = i"
            @approve="approve(i)"
            @regen="regen(i)"
          >
            <OverviewChart v-if="i === 0" v-bind="data.overview" />
            <StructureChart v-else-if="i === 1" v-bind="data.structure" />
            <BehaviourChart v-else-if="i === 2" v-bind="data.behaviour" />
            <WaterfallChart v-else-if="i === 3" v-bind="data.waterfall" />
            <DumbbellChart v-else-if="i === 4" v-bind="data.dumbbell" />
            <OptimisationChart v-else-if="i === 5" v-bind="data.optimisation" />
            <AdviceBlock v-else-if="i === 6" :advice="data.advice" />
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
        :versions="data.versions"
        :pending="pending"
        @resolve="resolve"
        @export="expOpen = true"
      />
    </div>

    <ExportDialog
      v-model:open="expOpen"
      :rows="comments"
      :pending="pending"
      :doc-no="data.export.docNo"
      :subtitle="data.export.subtitle"
      :title="data.export.title"
      :foot-note="data.export.footNote"
      @excel="exportExcel"
      @push="pushComments"
    />
  </section>
</template>
