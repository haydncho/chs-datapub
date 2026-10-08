<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { usePageData, runAction } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import { B6_SEED, type B6Course, type B6Doc, type B6DocKind, type B6Icon, type B6Tone } from '@/mock/B6'

const data = usePageData('B6', B6_SEED)

const ICON: Record<B6Icon, string> = {
  book: 'M4 4h7a3 3 0 0 1 3 3v13a2 2 0 0 0-2-2H4zM20 4h-7a3 3 0 0 0-3 3v13a2 2 0 0 1 2-2h8z',
  chart: 'M4 18h16M7 14v4M12 9v9M17 5v13',
  shield: 'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3zM8.5 12l2.5 2.5 4.5-5',
  chat: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
}
const TONE: Record<B6Tone, { bg: string; fg: string }> = {
  brand: { bg: 'var(--brand-soft)', fg: 'var(--brand)' },
  warn: { bg: 'var(--warn-soft)', fg: 'var(--warn-ink)' },
  ok: { bg: 'var(--ok-soft)', fg: 'var(--ok-ink)' },
}
const DOC_CLASS: Record<B6DocKind, string> = {
  国家: 'bg-bad-soft text-bad-ink',
  本市: 'bg-brand-soft text-brand',
  口径: 'bg-surface-3 text-ink-3',
}

/** server-confirmed progress from this session (on top of the payload's own state) */
const local = reactive<Record<string, { started?: boolean; done?: boolean; score?: number }>>({})
const st = (c: B6Course) => ({
  started: c.started || !!local[c.id]?.started,
  done: c.done || !!local[c.id]?.done,
  score: local[c.id]?.score ?? c.score,
})
const required = computed(() => data.value.courses.filter(c => c.tag === '必修'))
const doneCount = computed(() => required.value.filter(c => st(c).done).length)
const total = computed(() => required.value.length)

/* ---------- course dialog: study → quiz → server grading */
const open = ref(false)
const course = ref<B6Course | null>(null)
const answers = ref<(number | null)[]>([])
const busy = ref(false)
const result = ref<{ score: number; passed: boolean; correct: number; total: number } | null>(null)

async function start(c: B6Course) {
  if (busy.value) return
  if (!st(c).started) {
    busy.value = true
    const r = await runAction('B6', 'startCourse', { id: c.id })
    busy.value = false
    if (!r.ok) return say(r.error || '开始学习失败')
    local[c.id] = { ...local[c.id], started: true }
  }
  course.value = c
  answers.value = c.quiz.map(() => null)
  result.value = null
  open.value = true
}

const allAnswered = computed(() => answers.value.length > 0 && answers.value.every(a => a !== null))

async function submitQuiz() {
  const c = course.value
  if (!c || busy.value) return
  if (!allAnswered.value) return say('请完成全部测验题后再提交')
  busy.value = true
  const r = await runAction<{ result?: { score: number; passed: boolean; correct: number; total: number } }>('B6', 'submitQuiz', { id: c.id, answers: answers.value })
  busy.value = false
  if (!r.ok) return say(r.error || '测验提交失败')
  const res = r.data?.result
  if (!res) return say('测验提交失败')
  result.value = res
  if (res.passed) {
    const prev = st(c).score
    local[c.id] = { started: true, done: true, score: Math.max(prev ?? 0, res.score) }
    say(`已完成“${c.name}” · 测验 ${res.score} 分`)
  } else {
    say(`测验 ${res.score} 分,未达 60 分,请复习后重试`)
  }
}

function retry() {
  if (!course.value) return
  answers.value = course.value.quiz.map(() => null)
  result.value = null
}

/* ---------- policy document viewer */
const docOpen = ref(false)
const doc = ref<B6Doc | null>(null)
function viewDoc(d: B6Doc) {
  doc.value = d
  docOpen.value = true
}
</script>

<template>
  <PageSection label="B6 政策培训">
    <PageHeader title="政策与培训" :subtitle="data.subtitle">
      <div class="w-[220px] max-sm:w-full">
        <div class="flex justify-between text-xs">
          <span class="text-ink-4">我的必修进度</span>
          <span class="yb-num font-semibold" data-testid="b6-progress">{{ doneCount }}/{{ total }}</span>
        </div>
        <div class="mt-1.5 h-1.5 rounded-[3px] bg-line-2">
          <div class="h-1.5 rounded-[3px] bg-ok transition-[width]" :style="{ width: (total ? (doneCount / total) * 100 : 0) + '%' }" />
        </div>
      </div>
    </PageHeader>

    <div class="grid grid-cols-1 gap-3.5 min-[480px]:grid-cols-2 xl:grid-cols-4">
      <div v-for="c in data.courses" :key="c.id" class="yb-card flex flex-col overflow-hidden" :data-testid="'b6-course-' + c.id">
        <div
          class="relative flex h-[110px] flex-col justify-between overflow-hidden p-3.5"
          :style="{ background: `linear-gradient(135deg, ${TONE[c.tone].bg} 0%, #FFFFFF 120%)` }"
        >
          <svg viewBox="0 0 24 24" width="88" height="88" fill="none" :stroke="TONE[c.tone].fg" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" class="absolute -right-2.5 -bottom-3.5 opacity-[.14]"><path :d="ICON[c.icon]" /></svg>
          <span class="relative flex items-center gap-1.5 text-xs font-semibold" :style="{ color: TONE[c.tone].fg }">
            <span class="flex size-[22px] items-center justify-center rounded-md bg-white">
              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" :stroke="TONE[c.tone].fg" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="ICON[c.icon]" /></svg>
            </span>{{ c.tag }}
          </span>
          <span class="yb-num relative text-[30px] font-semibold" :style="{ color: TONE[c.tone].fg }">{{ c.minutes }}<span class="text-[13px]"> 分钟</span></span>
        </div>
        <div class="flex flex-1 flex-col gap-2 p-3.5">
          <div class="font-semibold">{{ c.name }}</div>
          <div class="flex-1 text-xs text-ink-4">{{ c.desc }}</div>
          <template v-if="st(c).done">
            <span class="text-xs font-medium text-ok-ink">✓ 已完成 · 测验 {{ st(c).score }} 分</span>
            <Button variant="ghost" class="h-8 font-normal text-ink-3 max-xl:h-10" @click="start(c)">复习课程</Button>
          </template>
          <template v-else>
            <span v-if="st(c).started" class="text-xs text-warn-ink">学习中 · 通过测验(≥ 60 分)后完成</span>
            <Button variant="soft" class="h-8 font-medium max-xl:h-10" :disabled="busy" @click="start(c)">{{ st(c).started ? '继续学习' : '开始学习' }}</Button>
          </template>
        </div>
      </div>
    </div>

    <div class="yb-card overflow-hidden">
      <div class="flex flex-wrap items-baseline justify-between gap-x-4 border-b border-line-2 px-card-x py-3.5">
        <span class="text-[15px] font-semibold">政策文件</span>
        <span class="text-xs text-ink-4">点击查看要点</span>
      </div>
      <button
        v-for="d in data.docs"
        :key="d.name"
        type="button"
        class="grid w-full cursor-pointer items-center gap-x-3.5 gap-y-1 yb-tr border-b border-line-3 px-card-x py-3 text-left last:border-b-0 hover:bg-surface-1 max-sm:grid-cols-[auto_minmax(0,1fr)] sm:grid-cols-[52px_minmax(0,1fr)_minmax(120px,auto)_64px] xl:grid-cols-[90px_minmax(0,1fr)_140px_90px] max-xl:min-h-12"
        :data-testid="'b6-doc'"
        @click="viewDoc(d)"
      >
        <span :class="['justify-self-start rounded px-2 py-px text-xs font-semibold', DOC_CLASS[d.kind]]">{{ d.kind }}</span>
        <span class="font-medium text-ink-1">{{ d.name }} <span class="text-[12px] font-normal text-brand">查看 →</span></span>
        <span class="text-xs text-ink-4 max-sm:col-start-2">{{ d.number }}</span>
        <span class="yb-num text-xs text-ink-4 max-sm:col-start-2 sm:text-right">{{ d.date }}</span>
      </button>
    </div>

    <!-- course: content + quiz -->
    <Dialog v-model:open="open">
      <DialogContent
        overlay-class="z-[96] bg-[rgba(11,21,38,.4)]"
        class="z-[96] flex max-h-[calc(100dvh-48px)] w-[min(560px,calc(100%-32px))] flex-col gap-4 overflow-y-auto rounded-2xl border-0 bg-white p-6 shadow-[0_24px_64px_rgba(11,21,38,.3)] max-sm:p-4"
      >
        <template v-if="course">
          <div>
            <DialogTitle class="text-lg font-semibold">{{ course.name }}</DialogTitle>
            <DialogDescription class="mt-0.5 text-xs text-ink-4">{{ course.tag }} · {{ course.minutes }} 分钟 · 学完后完成测验,≥ 60 分计为完成</DialogDescription>
          </div>
          <section v-for="(s, i) in course.sections" :key="i" class="flex flex-col gap-1">
            <h3 class="text-[13px] font-semibold">{{ i + 1 }}. {{ s.title }}</h3>
            <p class="text-[13px] leading-relaxed text-ink-3">{{ s.text }}</p>
          </section>
          <div class="flex flex-col gap-3 rounded-xl bg-surface-1 p-4" data-testid="b6-quiz">
            <div class="text-[13px] font-semibold">课程测验</div>
            <fieldset v-for="(q, qi) in course.quiz" :key="qi" class="flex flex-col gap-1.5" :disabled="!!result?.passed">
              <legend class="mb-1 text-[13px]">{{ qi + 1 }}. {{ q.q }}</legend>
              <label
                v-for="(o, oi) in q.options"
                :key="oi"
                :class="cn('flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-[13px] max-xl:min-h-11', answers[qi] === oi ? 'border-brand-line bg-brand-soft' : 'border-line-1 bg-white')"
              >
                <input v-model="answers[qi]" type="radio" :name="'q' + qi" :value="oi" class="accent-[var(--brand)]" />
                {{ o }}
              </label>
            </fieldset>
            <div v-if="result" :class="cn('rounded-lg px-3 py-2 text-[13px]', result.passed ? 'bg-ok-soft text-ok-ink' : 'bg-bad-soft text-bad-ink')" role="status" data-testid="b6-quiz-result">
              {{ result.passed ? '✓ 测验通过' : '未通过' }} · {{ result.score }} 分(答对 {{ result.correct }}/{{ result.total }})
            </div>
          </div>
          <div class="flex justify-end gap-2">
            <Button variant="outline" class="h-9 font-normal max-xl:h-11 max-xl:px-5" @click="open = false">{{ result?.passed ? '完成' : '稍后再学' }}</Button>
            <Button v-if="result && !result.passed" class="h-9 max-xl:h-11 max-xl:px-5" @click="retry">重新作答</Button>
            <Button v-else-if="!result" class="h-9 max-xl:h-11 max-xl:px-5" :disabled="busy || !allAnswered" @click="submitQuiz">提交测验</Button>
          </div>
        </template>
      </DialogContent>
    </Dialog>

    <!-- policy document -->
    <Dialog v-model:open="docOpen">
      <DialogContent
        overlay-class="z-[96] bg-[rgba(11,21,38,.4)]"
        class="z-[96] flex max-h-[calc(100dvh-48px)] w-[min(560px,calc(100%-32px))] flex-col gap-3 overflow-y-auto rounded-2xl border-0 bg-white p-6 shadow-[0_24px_64px_rgba(11,21,38,.3)] max-sm:p-4"
      >
        <template v-if="doc">
          <div>
            <DialogTitle class="text-lg font-semibold">{{ doc.name }}</DialogTitle>
            <DialogDescription class="mt-0.5 text-xs text-ink-4">{{ doc.issuer }} · {{ doc.number }} · {{ doc.date }}</DialogDescription>
          </div>
          <p class="text-[13px] leading-relaxed text-ink-2" data-testid="b6-doc-summary">{{ doc.summary }}</p>
          <div>
            <div class="mb-1.5 text-[13px] font-semibold">与定点医药机构相关的要点</div>
            <ul class="flex list-disc flex-col gap-1 pl-5 text-[13px] text-ink-3">
              <li v-for="p in doc.points" :key="p">{{ p }}</li>
            </ul>
          </div>
          <div class="flex justify-end">
            <Button variant="outline" class="h-9 font-normal max-xl:h-11 max-xl:px-5" @click="docOpen = false">关闭</Button>
          </div>
        </template>
      </DialogContent>
    </Dialog>
  </PageSection>
</template>
