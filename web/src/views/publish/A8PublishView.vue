<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { publishApi, type AudienceVersion, type FlowDetail, type IndicatorRequest, type Scope, type TierRequest, type TodoGroup, type TodoItem } from '@/api/publish'
import ApprovalPanel from '@/components/publish/ApprovalPanel.vue'
import AudienceVersions from '@/components/publish/AudienceVersions.vue'
import CorrectionPanel from '@/components/publish/CorrectionPanel.vue'
import IndicatorApproval from '@/components/publish/IndicatorApproval.vue'
import PackageScope from '@/components/publish/PackageScope.vue'
import StepBar from '@/components/publish/StepBar.vue'
import TierApproval from '@/components/publish/TierApproval.vue'
import TodoList from '@/components/publish/TodoList.vue'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import SegTabs from '@/components/shared/SegTabs.vue'
import Tag from '@/components/shared/Tag.vue'
import { confirm } from '@/lib/confirm'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'
import { useAuthStore } from '@/stores/auth'

/**
 * A8 发布工作流：分析监测区数据只能经过十步工作流、整包审批后才进入发布区。
 * 左侧按流程类型分组的待办（含 A5 报告草稿、A13 档位切换单、A4 指标上线审批）；右侧十步进度条与四个标签页。
 */
const page = pageDef('A8')!
const auth = useAuthStore()
const isConvener = computed(() => auth.user?.role === 'CONVENER')

const groups = ref<TodoGroup[]>([])
const sel = ref<{ type: 'flow' | 'tier' | 'indicator'; id: number } | null>(null)
const detail = ref<FlowDetail | null>(null)
const tier = ref<TierRequest | null>(null)
const indicator = ref<IndicatorRequest | null>(null)
const versions = ref<AudienceVersion[]>([])
const busy = ref(false)
const allItems = computed(() => groups.value.flatMap((g) => g.items))
const pendingGate = computed(() => allItems.value.filter((i) => i.status.includes('召集人审批') || i.type !== 'flow').length)
const dueSoon = computed(() => allItems.value.filter((i) => i.dueTone === 'danger' || i.dueTone === 'warning').length)

const TABS = [
  { value: 'pkg', label: '发布包与定向范围' },
  { value: 'ver', label: '分受众版本预览' },
  { value: 'appr', label: '审批与留痕' },
  { value: 'fix', label: '更正与撤回' },
]
const tab = ref('pkg')

async function loadTodos() {
  groups.value = await publishApi.todos()
}

async function open(it: { type: 'flow' | 'tier' | 'indicator'; id: number }) {
  sel.value = { type: it.type, id: it.id }
  if (it.type === 'flow') {
    const d = await publishApi.flow(it.id)
    if (sel.value?.type === 'flow' && sel.value.id === it.id) detail.value = d
  } else if (it.type === 'tier') {
    const t = await publishApi.tierRequest(it.id)
    if (sel.value?.type === 'tier' && sel.value.id === it.id) tier.value = t
  } else {
    const q = await publishApi.indicatorRequest(it.id)
    if (sel.value?.type === 'indicator' && sel.value.id === it.id) indicator.value = q
  }
}

const select = (it: TodoItem) => open(it).catch(notifyError)

/** 默认打开第一个待召集人审批的流程。 */
function firstItem(): TodoItem | undefined {
  const all = groups.value.flatMap((g) => g.items)
  return all.find((i) => i.type === 'flow' && i.statusTone === 'danger') ?? all[0]
}

onMounted(async () => {
  try {
    await loadTodos()
    const it = firstItem()
    if (it) await open(it)
    versions.value = await publishApi.audienceVersions()
  } catch (e) {
    notifyError(e)
  }
})

/** 定向范围：每次调整即保存，引擎重算覆盖数与名单；只采用最后一次请求的结果。 */
let seq = 0
async function changeScope(s: Scope) {
  if (!detail.value) return
  const id = detail.value.flow.id
  const mine = ++seq
  detail.value.scope = s
  try {
    const r = await publishApi.scope(id, s)
    if (mine !== seq || detail.value?.flow.id !== id) return
    detail.value.scope = r.scope
    detail.value.coverage = r.coverage
  } catch (e) {
    notifyError(e)
    if (mine === seq) await open({ type: 'flow', id }).catch(() => undefined)
  }
}

type Result = { message: string; id?: number }

/** 执行动作后刷新待办与当前详情。 */
async function act(fn: () => Promise<Result>, after?: (r: Result) => Promise<unknown> | void) {
  busy.value = true
  try {
    const r = await fn()
    notify(r.message)
    await loadTodos()
    if (after) await after(r)
    else if (sel.value) await open(sel.value)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

const fid = () => detail.value!.flow.id
const approve = async (op: string) => {
  const n = detail.value?.coverage?.count
  if (!(await confirm({ title: '批准发布', body: `批准即整包放行:数据由分析监测区进入发布区,按定向范围推送${n != null ? ` ${n} 家` : ''}机构。此操作留痕,批准后只能通过更正与撤回处理。`, okText: '批准发布' }))) return
  await act(() => publishApi.approve(fid(), op))
}
const reject = (op: string) => act(() => publishApi.reject(fid(), op))
const submit = () => act(() => publishApi.submit(fid()))
const advance = () => act(() => publishApi.advance(fid()))
const initiate = (action: '更正' | '撤回', reason: string) =>
  act(
    () => publishApi.correct(fid(), action, reason),
    async (r) => {
      if (!r.id) return
      await open({ type: 'flow', id: r.id })
      tab.value = 'appr'
    },
  )

/** 档位切换单处理后离开待办：自动打开下一项。 */
async function afterTier() {
  const it = firstItem()
  if (it) await open(it)
  else sel.value = null
}
const approveTier = async (op: string) => {
  if (!(await confirm({ title: '批准对标档位切换', body: `批准后「${tier.value!.indicator}」将按新档位向机构展示,此操作留痕且不可撤销。`, okText: '批准切换' }))) return
  await act(() => publishApi.approveTier(tier.value!.id, op), afterTier)
}
const rejectTier = (op: string) => act(() => publishApi.rejectTier(tier.value!.id, op), afterTier)
const approveIndicator = async (op: string) => {
  if (!(await confirm({ title: '批准指标上线', body: `批准后「${indicator.value!.name}」正式上线并进入展示策略配置,此操作留痕。`, okText: '批准上线' }))) return
  await act(() => publishApi.approveIndicator(indicator.value!.id, op), afterTier)
}
const rejectIndicator = (op: string) => act(() => publishApi.rejectIndicator(indicator.value!.id, op), afterTier)

const f = computed(() => detail.value?.flow)
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <div class="mt-5 grid grid-cols-4 gap-3.5" data-testid="a8-kpis">
      <KpiCard label="在办事项" :value="String(allItems.length)" unit="项" icon="send" tone="primary" :spark="groups.map((g) => g.items.length)" desc="按流程类型分组,柱高为各类数量" />
      <KpiCard label="待召集人审批" :value="String(pendingGate)" unit="项" icon="shield" tone="warning" desc="含发布包、指标上线、档位切换" />
      <KpiCard label="时限临近 / 超期" :value="String(dueSoon)" unit="项" icon="clock" :tone="dueSoon ? 'danger' : 'success'" desc="请优先处理" />
      <KpiCard label="流程类型" :value="String(groups.length)" unit="类" icon="flow" tone="ai" desc="月 / 季 / 年 / 专题 / 预警 / 更正" />
    </div>

    <div class="mt-5 grid grid-cols-[288px_minmax(0,1fr)] items-start gap-3.5">
      <Panel title="我的待办" class="px-3!">
        <TodoList :groups="groups" :selected="sel" @select="select" />
      </Panel>

      <div v-if="sel?.type === 'tier' && tier" class="min-w-0">
        <TierApproval :req="tier" :can-approve="isConvener" :busy="busy" @approve="approveTier" @reject="rejectTier" />
      </div>

      <div v-else-if="sel?.type === 'indicator' && indicator" class="min-w-0">
        <IndicatorApproval :req="indicator" :can-approve="isConvener" :busy="busy" @approve="approveIndicator" @reject="rejectIndicator" />
      </div>

      <div v-else-if="detail && f" class="flex min-w-0 flex-col gap-3.5" :data-flow="f.name">
        <section class="rounded-2xl border border-line-soft bg-surface shadow-card px-[18px] pt-3.5 pb-4">
          <div class="flex flex-wrap items-center gap-2.5">
            <span class="text-[16px] font-semibold text-ink" data-testid="flow-name">{{ f.name }}</span>
            <Tag>{{ f.kind }}</Tag>
            <Tag tone="primary">发布包 {{ f.packageVersion }} · 整包审批</Tag>
            <Tag v-if="f.reportDraftId" tone="ai">报告草稿 #{{ f.reportDraftId }}</Tag>
            <Tag v-if="f.action" :tone="f.action === '撤回' ? 'danger' : 'warning'">{{ f.action }}</Tag>
            <span class="ml-auto text-[12px] text-ink-sub" data-testid="flow-step">当前:{{ f.stepLabel }}</span>
          </div>
          <div class="mt-4">
            <StepBar :steps="detail.steps" :step="f.step" :archived="f.archived" />
          </div>
        </section>

        <section class="rounded-2xl border border-line-soft bg-surface shadow-card">
          <div class="flex items-center gap-3 border-b border-divider px-[18px] py-2.5">
            <SegTabs v-model="tab" :items="TABS" data-testid="a8-tabs" />
            <span class="ml-auto truncate text-[11px] text-ink-faint">发布物:{{ f.subject }}</span>
          </div>
          <div class="px-[18px] py-4">
            <PackageScope v-if="tab === 'pkg'" :detail="detail" :coverage="detail.coverage" :busy="busy" @change="changeScope" />
            <AudienceVersions v-else-if="tab === 'ver'" :versions="versions" />
            <ApprovalPanel v-else-if="tab === 'appr'" :detail="detail" :busy="busy" @approve="approve" @reject="reject" @submit="submit" @advance="advance" />
            <CorrectionPanel v-else :detail="detail" :busy="busy" @initiate="initiate" />
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
