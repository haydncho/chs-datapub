<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { alertApi, exportApi, opinionApi } from '@/api'
import { recommendApi } from '@/api/indicator'
import { publishApi } from '@/api/publish'
import type { Tone } from '@/api/types'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { pageDef } from '@/lib/nav'
import { useAuthStore } from '@/stores/auth'

/**
 * 工作台(召集人 / 行政管理组首页):先回答「今天我要处理什么」——待审批、临近超期的意见、待发出的预警、待审批的导出、
 * 待确认的选题——按紧急程度排序,每条一键直达;右侧是月度发布主线当前走到哪一步。
 */
const page = pageDef('W0')!
const router = useRouter()
const auth = useAuthStore()

type Kind = '审批' | '意见' | '预警' | '导出' | '选题'
interface Task { kind: Kind; title: string; meta: string; tone: Tone; to: string; rank: number }
const tasks = ref<Task[]>([])
const flow = ref<{ name: string; step: string; to: string } | null>(null)
const filter = ref<'全部' | Kind>('全部')
const loaded = ref(false)
const rankOf = (t: Tone) => (t === 'danger' ? 0 : t === 'warning' ? 1 : 2)

onMounted(async () => {
  const [todo, op, al, ex, top] = await Promise.allSettled([publishApi.todos(), opinionApi.list('全部'), alertApi.triggers(), exportApi.pending(), recommendApi.topics()])
  const out: Task[] = []
  if (todo.status === 'fulfilled') {
    const items = todo.value.flatMap((g) => g.items)
    for (const i of items) {
      if (i.status === '已归档') continue
      const gate = i.type !== 'flow' || i.status.includes('召集人审批')
      out.push({ kind: '审批', title: i.name, meta: `${i.status} · ${i.due}`, tone: gate ? (i.dueTone === 'muted' ? 'warning' : i.dueTone) : i.dueTone, to: '/a8', rank: gate ? 0 : 3 })
    }
    const m = items.find((i) => i.type === 'flow' && i.name.includes('月度') && i.status !== '已归档')
    if (m) flow.value = { name: m.name, step: m.status, to: '/w1' }
  }
  if (op.status === 'fulfilled') {
    for (const r of op.value.rows.filter((x) => x.status !== 'DONE')) out.push({ kind: '意见', title: `${r.org} · ${r.ref}`, meta: `${r.category} · ${r.dueLabel}`, tone: r.dueTone, to: '/a10', rank: rankOf(r.dueTone) })
  }
  if (al.status === 'fulfilled') {
    for (const t of al.value.filter((x) => x.status === 'GEN')) out.push({ kind: '预警', title: `${t.org} · ${t.rule}`, meta: `待发出提醒函 · ${t.value}`, tone: 'warning', to: '/a11', rank: 1 })
    for (const t of al.value.filter((x) => x.status === 'SENT')) out.push({ kind: '预警', title: `${t.org} · ${t.rule}`, meta: '已发出 · 等待机构回执', tone: 'muted', to: '/a11', rank: 3 })
  }
  if (ex.status === 'fulfilled') {
    for (const e of ex.value) out.push({ kind: '导出', title: `${e.applicant} 申请导出「${e.content}」`, meta: `${e.purpose} · ${e.validity}`, tone: 'warning', to: '/e1', rank: 1 })
  }
  if (top.status === 'fulfilled') {
    const n = top.value.rows.filter((r) => r.status === 'CAND').length
    if (n) out.push({ kind: '选题', title: `${n} 个选题候选待人工确认`, meta: top.value.period, tone: 'primary', to: '/a6', rank: 2 })
  }
  tasks.value = out.sort((a, b) => a.rank - b.rank)
  loaded.value = true
})

const shown = computed(() => tasks.value.filter((t) => filter.value === '全部' || t.kind === filter.value))
const urgent = computed(() => tasks.value.filter((t) => t.rank === 0).length)
const count = (k: Kind) => tasks.value.filter((t) => t.kind === k).length
const TABS: ('全部' | Kind)[] = ['全部', '审批', '意见', '预警', '导出', '选题']
const toneDot: Record<Tone, string> = { danger: 'bg-danger', warning: 'bg-warning', primary: 'bg-primary-solid', success: 'bg-success-solid', ai: 'bg-ai', muted: 'bg-neutral' }
const hello = computed(() => {
  const h = new Date().getHours()
  return `${h < 6 ? '夜深了' : h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好'},${auth.user?.name ?? ''}`
})
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" title="工作台" />
    <div class="mt-4 text-[14px] text-ink-sub">{{ hello }}。{{ loaded ? (tasks.length ? `今天有 ${tasks.length} 件事等你处理,其中 ${urgent} 件需要优先办理。` : '今天没有待办事项。') : '正在汇总待办…' }}</div>

    <div class="mt-4 grid grid-cols-4 gap-3.5" data-testid="home-kpis">
      <KpiCard label="待我处理" :value="String(tasks.length)" unit="件" icon="grip" tone="primary" desc="审批、意见、预警、导出、选题" />
      <KpiCard label="优先办理" :value="String(urgent)" unit="件" icon="alert" :tone="urgent ? 'danger' : 'success'" :desc="urgent ? '召集人审批关口与超期事项' : '暂无紧急事项'" />
      <KpiCard label="待审批" :value="String(count('审批'))" unit="项" icon="shield" tone="warning" desc="发布包 / 指标上线 / 档位切换" />
      <KpiCard label="机构意见待办理" :value="String(count('意见'))" unit="条" icon="opinion" tone="ai" desc="含核对期异议" />
    </div>

    <div class="mt-4 grid grid-cols-[minmax(0,1fr)_340px] items-start gap-4">
      <Panel title="今日待办" icon="grip" flush>
        <template #head>
          <div class="flex gap-1.5">
            <button
              v-for="t in TABS"
              :key="t"
              type="button"
              class="cursor-pointer rounded-full border px-3 py-1 text-[12px] transition-colors"
              :class="t === filter ? 'border-primary bg-primary-tint font-semibold text-primary' : 'border-line bg-surface text-ink-sub hover:bg-hover'"
              :data-filter="t"
              @click="filter = t"
            >{{ t }}<span v-if="t !== '全部'" class="ml-1 text-ink-faint">{{ count(t) }}</span></button>
          </div>
        </template>
        <div v-if="!loaded" class="mx-5 mb-5 h-40 animate-pulse rounded-xl bg-subtle" />
        <div v-else-if="!shown.length" class="px-5 pb-8 pt-4 text-center text-[13px] text-ink-muted" data-testid="home-empty">这一类没有待办,清爽。</div>
        <ul v-else class="px-2 pb-3" data-testid="home-tasks">
          <li v-for="(t, i) in shown" :key="i">
            <button type="button" class="flex w-full cursor-pointer items-center gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-hover" :data-task="t.kind" @click="router.push(t.to)">
              <span class="size-2.5 flex-none rounded-full" :class="toneDot[t.tone]" />
              <Tag :tone="t.tone === 'muted' ? 'muted' : t.tone">{{ t.kind }}</Tag>
              <span class="min-w-0 flex-1 truncate text-[14px] font-medium text-ink">{{ t.title }}</span>
              <span class="flex-none text-[12px] text-ink-muted">{{ t.meta }}</span>
              <span class="flex-none text-ink-ghost">›</span>
            </button>
          </li>
        </ul>
      </Panel>

      <div class="flex flex-col gap-3.5">
        <Panel title="月度发布主线" icon="send">
          <template v-if="flow">
            <div class="text-[14px] font-semibold text-ink">{{ flow.name }}</div>
            <div class="mt-1 text-[12px] text-ink-muted">当前:{{ flow.step }}</div>
            <Button class="mt-3 w-full" data-testid="home-guide" @click="router.push(flow.to)">打开月度发布向导 →</Button>
          </template>
          <div v-else class="text-[12px] text-ink-muted">暂无进行中的月度发布。</div>
        </Panel>
        <Panel title="快捷入口" icon="holo">
          <div class="grid grid-cols-2 gap-2.5 text-[13px]">
            <button v-for="l in [['全息图', '/a2'], ['指标与算法', '/a4'], ['响应中心', '/a10'], ['我的导出', '/e1']]" :key="l[0]" type="button" class="cursor-pointer rounded-xl border border-line-soft bg-surface px-3 py-2.5 text-left font-medium text-ink transition-colors hover:border-primary hover:bg-primary-tint" @click="router.push(l[1])">{{ l[0] }}</button>
          </div>
        </Panel>
      </div>
    </div>
  </div>
</template>
