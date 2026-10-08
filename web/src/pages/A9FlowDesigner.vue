<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { getJson, runAction, usePageData } from '@/api/client'
import { say } from '@/app/shell'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/yb'
import { cn } from '@/lib/utils'
import { A9_SEED, type A9Data, type A9Kind, type A9Node } from '@/mock/A9'
import FlowCanvas from './A9/FlowCanvas.vue'
import FlowChecks from './A9/FlowChecks.vue'
import FlowGantt from './A9/FlowGantt.vue'
import NodePanel from './A9/NodePanel.vue'
import { COLS, PALETTE, type ColSpan, type FlowNode } from './A9/model'

const data = usePageData('A9', A9_SEED)

const flowName = ref('月告知')
const sel = ref(2)
const busy = ref(false)

const APPROVER = '召集人'
const SIGNER = '定点医疗机构'

/** default 超时动作 index by node kind (as in the prototype) */
const defaultTimeout = (k: A9Kind) => (k === '审批' ? 2 : k === '签收' ? 1 : 0)

/** editable copy of every flow (node add / delete / lane / days / timeout / channels), rebuilt from the server copy */
const drafts = reactive<Record<string, A9Node[]>>({})
function normalise(n: A9Node): A9Node {
  return {
    lane: n.lane,
    col: n.col,
    name: n.name,
    kind: n.kind,
    days: n.days,
    timeoutAction: n.timeoutAction ?? data.value.timeoutActions[defaultTimeout(n.kind)],
    channels: [...(n.channels ?? data.value.defaultChannels)],
  }
}
function resetDrafts() {
  for (const k of Object.keys(drafts)) delete drafts[k]
  for (const f of data.value.flows) drafts[f.name] = f.nodes.map(normalise)
}
watch(data, resetDrafts, { immediate: true })

const flow = computed(() => data.value.flows.find(f => f.name === flowName.value) ?? data.value.flows[0]!)
const draft = computed<A9Node[]>(() => drafts[flow.value.name] ?? [])

const nodes = computed<FlowNode[]>(() =>
  draft.value.map((x, i) => ({ idx: i, lane: x.lane, col: x.col, name: x.name, kind: x.kind, days: x.days })),
)
const usedLanes = computed(() => data.value.lanes.filter(l => nodes.value.some(n => n.lane === l)))

/** cumulative stage ends: each column takes its longest non-optional node */
const cols = computed<ColSpan[]>(() => {
  const out: ColSpan[] = []
  let acc = 0
  for (let c = 0; c < COLS; c++) {
    const ns = nodes.value.filter(n => n.col === c)
    const m = ns.length ? Math.max(0, ...ns.filter(n => n.kind !== '可选').map(n => n.days)) : 0
    acc += m
    out.push({ has: ns.length > 0, end: acc, start: acc - m })
  }
  return out
})
const total = computed(() => cols.value[COLS - 1]?.end ?? 0)
const limit = computed(() => data.value.legalLimit)
const over = computed(() => total.value > limit.value)

const ni = computed(() => Math.max(0, Math.min(sel.value, nodes.value.length - 1)))
const node = computed(() => nodes.value[ni.value]!)
const cur = computed(() => draft.value[ni.value]!)
const timeoutIdx = computed(() => Math.max(0, data.value.timeoutActions.indexOf(cur.value?.timeoutAction ?? '')))
const channelOn = computed(() => Object.fromEntries((cur.value?.channels ?? []).map(c => [c, true])) as Record<string, boolean>)
const note = computed(() => {
  const n = node.value
  if (!n) return ''
  if (n.kind === '并行') return '与同列节点并行办理,全部完成后进入下一阶段。'
  if (nodes.value.filter(y => y.col === n.col).length > 1) return '同列存在并行节点,以耗时最长者计入关键路径。'
  return '串行节点 · 完成后自动流转至下一阶段。'
})

/** same rules as the server (FlowRules): the publish button stays disabled while any fails */
const checks = computed(() => {
  const ns = draft.value
  const used = Array.from({ length: COLS }, (_, c) => ns.some(n => n.col === c))
  const last = used.lastIndexOf(true)
  const contiguous = used[0] === true && used.slice(0, last + 1).every(Boolean)
  const approvals = ns.filter(n => n.kind === '审批')
  const signs = ns.filter(n => n.kind === '签收')
  return [
    { ok: contiguous, text: contiguous ? '起止节点完整' : '起止节点不完整 · 须从「发起」开始且阶段连续' },
    {
      ok: approvals.length > 0 && approvals.every(n => n.lane === APPROVER),
      text: approvals.length === 0 ? '缺少召集人审批节点' : approvals.every(n => n.lane === APPROVER) ? '包含召集人审批节点' : '审批节点须由召集人承办',
    },
    {
      ok: signs.some(n => n.lane === SIGNER),
      text: signs.some(n => n.lane === SIGNER) ? '机构签收节点已配置' : '须有由定点医疗机构承办的签收节点',
    },
    { ok: !over.value, text: '关键路径 ' + total.value + ' 天 ' + (over.value ? '>' : '≤') + ' 法定 ' + limit.value + ' 天' },
  ]
})
const valid = computed(() => checks.value.every(c => c.ok))

/** what changed against the published version — stored as the version note */
const changes = computed(() => {
  const before = flow.value.nodes.map(normalise)
  const after = draft.value
  const out: string[] = []
  const byName = (arr: A9Node[]) => new Map(arr.map(n => [n.name, n]))
  const b = byName(before)
  const a = byName(after)
  for (const n of after) {
    const o = b.get(n.name)
    if (!o) { out.push('新增「' + n.name + '」'); continue }
    if (o.days !== n.days) out.push(n.name + ' 时限 ' + o.days + '→' + n.days + ' 天')
    if (o.lane !== n.lane) out.push(n.name + ' 承办改为' + n.lane)
    if (o.col !== n.col) out.push(n.name + ' 移至阶段 ' + (n.col + 1))
    if (o.timeoutAction !== n.timeoutAction) out.push(n.name + ' 超时' + n.timeoutAction)
    if ((o.channels ?? []).join() !== (n.channels ?? []).join()) out.push(n.name + ' 通知渠道调整')
  }
  for (const o of before) if (!a.has(o.name)) out.push('删除「' + o.name + '」')
  return out
})
const dirty = computed(() => changes.value.length > 0)

function pickFlow(name: string) {
  flowName.value = name
  sel.value = 0
}
function setLane(l: string) { if (cur.value) cur.value.lane = l }
function setDays(d: number) { if (cur.value) cur.value.days = Math.max(0, Math.min(60, Math.round(d))) }
function setTimeout_(i: number) { if (cur.value) cur.value.timeoutAction = data.value.timeoutActions[i] }
function toggleChannel(c: string) {
  if (!cur.value) return
  const on = cur.value.channels ?? []
  cur.value.channels = on.includes(c) ? on.filter(x => x !== c) : data.value.channels.filter(x => x === c || on.includes(x))
}
function rename(name: string) { if (cur.value) cur.value.name = name.slice(0, 16) }

const DEFAULT_LANE: Record<A9Kind, string> = { 审批: APPROVER, 签收: SIGNER, 人工: '行政管理组', 自动: '行政管理组', 并行: '专家组', 可选: SIGNER }
const DEFAULT_DAYS: Record<A9Kind, number> = { 审批: 2, 签收: 3, 人工: 1, 自动: 0, 并行: 2, 可选: 5 }

/** 节点库: click adds after the selected node (并行 beside it); drop places it on the lane / stage under the pointer */
function addNode(p: { kind: A9Kind; col?: number; lane?: string }) {
  const list = drafts[flow.value.name]
  if (!list) return
  if (list.length >= 24) {
    say('节点不能超过 24 个')
    return
  }
  const selCol = list[ni.value]?.col ?? 0
  const col = p.col ?? (p.kind === '并行' ? selCol : Math.min(COLS - 1, selCol + 1))
  const base = PALETTE.find(x => x.kind === p.kind)?.label.replace('节点', '') ?? p.kind
  let name = base
  for (let i = 2; list.some(n => n.name === name); i++) name = base + i
  list.push(normalise({ lane: p.lane ?? DEFAULT_LANE[p.kind], col, name, kind: p.kind, days: DEFAULT_DAYS[p.kind] }))
  sel.value = list.length - 1
  say('已添加「' + name + '」· 阶段 ' + (col + 1) + ' · ' + (p.lane ?? DEFAULT_LANE[p.kind]))
}
function removeNode() {
  const list = drafts[flow.value.name]
  if (!list || !cur.value) return
  if (list.length <= 1) {
    say('流程至少保留 1 个节点')
    return
  }
  const name = cur.value.name
  list.splice(ni.value, 1)
  sel.value = Math.max(0, ni.value - 1)
  say('已删除「' + name + '」· 发布新版本后生效')
}
function moveNode(col: number) { if (cur.value) cur.value.col = Math.max(0, Math.min(COLS - 1, col)) }

async function refresh() {
  try {
    const remote = await getJson<A9Data>('/pages/A9')
    if (remote && typeof remote === 'object') data.value = { ...A9_SEED, ...remote }
  } catch { /* keep */ }
}

async function publish() {
  const failed = checks.value.find(c => !c.ok)
  if (failed) {
    say(over.value ? '关键路径超过法定期限 ' + limit.value + ' 个工作日,请调整' : '流程校验未通过:' + failed.text)
    return
  }
  if (!dirty.value) {
    say('流程没有改动,无需发布新版本')
    return
  }
  if (busy.value) return
  busy.value = true
  const name = flow.value.name
  const version = flow.value.version + 1
  const r = await runAction('A9', 'publishFlowVersion', {
    flow: name,
    version,
    total: total.value,
    note: changes.value.join(';').slice(0, 120),
    nodes: draft.value.map(n => ({ ...n, channels: [...(n.channels ?? [])] })),
  })
  busy.value = false
  if (!r.ok) {
    say(r.error || '发布未成功')
    return
  }
  await refresh()
  flowName.value = name
  say('已发布 ' + name + '流程 v' + version + ' · 新版本生效')
}

const versions = computed(() => flow.value.versions ?? data.value.versions)
</script>

<template>
  <section data-screen-label="A9 流程设计器" class="mx-auto flex w-full max-w-[1600px] flex-col gap-3.5 px-8 pt-6 pb-14 max-xl:gap-4 max-xl:px-6 max-lg:px-4 max-xl:[&_.text-\[10px\]]:text-[11px] max-xl:[&_.text-\[11px\]]:text-xs">
    <PageHeader subtitle="按承办角色分泳道 · 同一列节点并行 · 点击节点编辑时限与超时动作">
      <template #subtitle><span class="inline-block whitespace-nowrap">按承办角色分泳道 · 同一列节点并行 ·&nbsp;</span><span class="inline-block whitespace-nowrap">点击节点编辑时限与超时动作</span></template>
      <template #title>
        <span class="tracking-[-0.2px] whitespace-nowrap">流程设计器</span>
        <span class="flex min-w-0 flex-wrap items-center gap-2.5 max-xl:gap-1.5">
          <span class="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] font-semibold whitespace-nowrap text-brand">{{ flow.name }}流程 v{{ flow.version }}{{ dirty ? ' · 有未发布修改' : '' }}</span>
          <span
            :class="cn('rounded-full px-2 py-0.5 text-[11px] font-semibold whitespace-nowrap', valid ? 'bg-ok-soft text-ok-ink' : 'bg-bad-soft text-bad-ink')"
          >{{ valid ? '校验通过' : over ? '超出法定期限' : '校验未通过' }}</span>
        </span>
      </template>
      <div class="flex rounded-[10px] bg-line-2 p-[3px]" role="group" aria-label="选择流程">
        <button
          v-for="f in data.flows"
          :key="f.name"
          type="button"
          :aria-pressed="f.name === flow.name"
          :class="cn(
            'cursor-pointer rounded-lg px-4 py-1.5 text-[13px] font-medium whitespace-nowrap max-xl:min-h-10',
            f.name === flow.name ? 'bg-white text-ink-1 shadow-[0_1px_3px_rgba(15,23,42,.1)]' : 'text-ink-4',
          )"
          @click="pickFlow(f.name)"
        >{{ f.name }}</button>
      </div>
      <Button v-if="dirty" variant="outline" class="h-9 font-normal max-xl:h-11" :disabled="busy" @click="resetDrafts">放弃修改</Button>
      <Button class="h-9 max-xl:h-11" :disabled="busy" @click="publish">{{ busy ? '发布中…' : '发布新版本' }}</Button>
    </PageHeader>

    <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
      <div class="flex min-w-0 flex-col gap-3.5 max-xl:gap-4">
        <FlowCanvas
          :nodes="nodes"
          :lanes="usedLanes"
          :stages="data.stages"
          :cols="cols"
          :selected="ni"
          @select="i => (sel = i)"
          @add="addNode"
        />
        <FlowGantt :nodes="nodes" :cols="cols" :total="total" :limit="limit" :selected="ni" />
      </div>
      <div class="flex flex-col gap-3 max-xl:gap-4 xl:sticky xl:top-(--sticky-panel)">
        <NodePanel
          v-if="node"
          :node="node"
          :note="note"
          :lanes="data.lanes"
          :stages="data.stages"
          :timeout-actions="data.timeoutActions"
          :timeout="timeoutIdx"
          :channels="data.channels"
          :channel-on="channelOn"
          :can-remove="nodes.length > 1"
          @lane="setLane"
          @days="setDays"
          @timeout="setTimeout_"
          @channel="toggleChannel"
          @name="rename"
          @col="moveNode"
          @remove="removeNode"
        />
        <FlowChecks :checks="checks" :versions="versions" />
      </div>
    </div>
  </section>
</template>
