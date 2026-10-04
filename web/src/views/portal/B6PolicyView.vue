<script setup lang="ts">
import { watchDebounced } from '@vueuse/core'
import { onMounted, ref, watch } from 'vue'
import { portalReportsApi, type Course, type PolicyDoc, type Quiz } from '@/api/portalReports'
import Chip from '@/components/shared/Chip.vue'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import StrokeIcon from '@/components/ui/stroke-icon/StrokeIcon.vue'
import { iconPaths } from '@/lib/icons'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/** B6 政策指南与培训：文件按类别筛选与检索（只读查看器打开，写审计）；课件学习进度；随堂单选题即时判分（答案在服务端判）。 */
const page = pageDef('B6')!
const cats = ref<string[]>([])
const cat = ref('全部')
const q = ref('')
const docs = ref<PolicyDoc[]>([])
const courses = ref<Course[]>([])
const quiz = ref<Quiz | null>(null)
const answering = ref(false)

async function loadDocs() {
  const r = await portalReportsApi.docs(cat.value, q.value.trim())
  cats.value = r.categories
  docs.value = r.rows
}

onMounted(async () => {
  try {
    const [, c, z] = await Promise.all([loadDocs(), portalReportsApi.courses(), portalReportsApi.quiz()])
    courses.value = c
    quiz.value = z.quiz
  } catch (e) {
    notifyError(e)
  }
})
watch(cat, () => loadDocs().catch(notifyError))
watchDebounced(q, () => loadDocs().catch(notifyError), { debounce: 300 })

async function open(d: PolicyDoc) {
  try {
    notify((await portalReportsApi.viewDoc(d.id)).message)
  } catch (e) {
    notifyError(e)
  }
}

const courseState = (c: Course): [string, string] => (c.pct >= 100 ? ['已完成', 'text-success'] : c.pct > 0 ? ['学习中', 'text-primary'] : ['未开始', 'text-ink-muted'])

async function pick(i: number) {
  const z = quiz.value
  if (!z || z.answered || answering.value) return
  answering.value = true
  try {
    z.answered = await portalReportsApi.answer(z.id, i)
  } catch (e) {
    notifyError(e)
  } finally {
    answering.value = false
  }
}

function optClass(i: number) {
  const a = quiz.value?.answered
  if (!a) return 'border-line bg-surface hover:border-primary hover:bg-hover-soft cursor-pointer'
  if (i === a.correctIndex) return 'border-success bg-success-soft text-success-ink'
  if (i === a.picked) return 'border-danger bg-danger-soft text-danger-ink'
  return 'border-line bg-surface text-ink-muted'
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <div class="mt-5 grid grid-cols-3 gap-3.5" data-testid="b6-kpis">
      <KpiCard label="政策文件" :value="String(docs.length)" unit="份" icon="book" tone="primary" desc="只读查看,带实名水印" />
      <KpiCard label="培训课程" :value="String(courses.length)" unit="门" icon="topic" tone="ai" :spark="courses.map((c) => c.pct)" desc="柱高为各课完成进度" />
      <KpiCard label="平均学习进度" :value="`${courses.length ? Math.round(courses.reduce((n, c) => n + c.pct, 0) / courses.length) : 0}%`" icon="check" tone="success" desc="含随堂测验" />
    </div>
    <div class="mt-5 grid grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] items-start gap-3">
      <!-- 文件检索 -->
      <div>
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <label class="flex h-[30px] w-[240px] items-center gap-1.5 rounded-lg border border-line bg-surface px-2.5 text-ink-faint focus-within:border-ring">
            <StrokeIcon :d="iconPaths.search" :size="13" />
            <input v-model="q" type="search" placeholder="搜索文件名称 / 文号" class="min-w-0 flex-1 bg-transparent text-[12px] text-ink outline-none placeholder:text-ink-faint" data-testid="doc-search" />
          </label>
          <Chip :on="cat === '全部'" @click="cat = '全部'">全部</Chip>
          <Chip v-for="c in cats" :key="c" :on="cat === c" :data-cat="c" @click="cat = c">{{ c }}</Chip>
        </div>
        <div class="table-scroll">
          <table class="data-table" data-testid="docs">
            <thead><tr><th>类别</th><th>文件名称</th><th>文号</th><th>发布日期</th><th>格式</th></tr></thead>
            <tbody class="text-ink-sub">
              <tr v-if="!docs.length"><td colspan="5" class="py-6 text-center text-ink-faint">未找到相关文件</td></tr>
              <tr v-for="d in docs" :key="d.id" class="cursor-pointer" :data-doc="d.id" @click="open(d)">
                <td><Tag>{{ d.category }}</Tag></td>
                <td class="font-medium text-ink">{{ d.title }}</td>
                <td>{{ d.docNo }}</td>
                <td>{{ d.issuedOn }}</td>
                <td class="text-ink-muted">{{ d.format }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="mt-2 text-[11px] text-ink-faint">文件在只读查看器中打开(带实名水印),不提供下载与转发。</div>
      </div>

      <div class="flex flex-col gap-3">
        <Panel title="培训课件" data-testid="courses">
          <div v-for="c in courses" :key="c.id" class="border-b border-divider py-2 text-[12px] last:border-b-0" :data-course="c.id">
            <div class="flex justify-between gap-2">
              <span class="font-medium text-ink">{{ c.title }}</span>
              <span :class="courseState(c)[1]">{{ courseState(c)[0] }}</span>
            </div>
            <div class="mt-1 flex items-center gap-2">
              <div class="h-1.5 flex-1 rounded-full bg-divider" role="progressbar" :aria-valuenow="c.pct" aria-valuemin="0" aria-valuemax="100">
                <div class="h-1.5 rounded-full bg-primary" :style="{ width: c.pct + '%' }" />
              </div>
              <span class="w-[36px] text-right text-ink-muted">{{ c.pct }}%</span>
              <span class="w-[56px] text-right text-ink-faint">{{ c.minutes }} 分钟</span>
            </div>
          </div>
        </Panel>

        <Panel v-if="quiz" :title="`随堂试题 · ${quiz.courseTitle}`" data-testid="quiz">
          <div class="mb-2.5 text-[12px] text-ink">{{ quiz.question }}</div>
          <div class="flex flex-col gap-1.5">
            <button
              v-for="(o, i) in quiz.options"
              :key="i"
              type="button"
              class="rounded-lg border px-2.5 py-2 text-left text-[12px] transition-colors"
              :class="optClass(i)"
              :disabled="!!quiz.answered || answering"
              :data-opt="i"
              :data-result="quiz.answered ? (i === quiz.answered.correctIndex ? 'right' : i === quiz.answered.picked ? 'wrong' : '') : ''"
              @click="pick(i)"
            ><b>{{ 'ABCDEFGH'[i] }}.</b> {{ o }}</button>
          </div>
          <div v-if="quiz.answered" class="mt-2 text-[12px]" data-testid="quiz-result">
            <span :class="quiz.answered.correct ? 'text-success' : 'text-danger'">{{ quiz.answered.result }}</span>
            <span class="text-ink-muted"> · {{ quiz.answered.explain }}</span>
          </div>
        </Panel>
      </div>
    </div>
  </div>
</template>
