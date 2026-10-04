<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/yb'
import { cn } from '@/lib/utils'
import { A9_SEED } from '@/mock/A9'
import FlowCanvas from './A9/FlowCanvas.vue'
import FlowChecks from './A9/FlowChecks.vue'
import FlowGantt from './A9/FlowGantt.vue'
import NodePanel from './A9/NodePanel.vue'
import { COLS, type ColSpan, type FlowNode } from './A9/model'

const data = usePageData('A9', A9_SEED)

const flowName = ref('月告知')
const sel = ref(2)

/** local edits keyed by `${flow}${nodeIdx}` (as in the prototype) */
const dur = reactive<Record<string, number>>({})
const lov = reactive<Record<string, string>>({})
const fot = reactive<Record<string, number>>({})
const fnt = reactive<Record<string, Record<string, boolean>>>({})

const flow = computed(() => data.value.flows.find(f => f.name === flowName.value) ?? data.value.flows[0]!)
const key = (i: number) => flow.value.name + i

const nodes = computed<FlowNode[]>(() =>
  flow.value.nodes.map((x, i) => ({
    idx: i,
    lane: lov[key(i)] ?? x.lane,
    col: x.col,
    name: x.name,
    kind: x.kind,
    days: dur[key(i)] ?? x.days,
  })),
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

const ni = computed(() => Math.min(sel.value, nodes.value.length - 1))
const node = computed(() => nodes.value[ni.value]!)
const timeoutIdx = computed(() => fot[key(ni.value)] ?? (node.value.kind === '审批' ? 2 : node.value.kind === '签收' ? 1 : 0))
const defaultChannels = () => Object.fromEntries(data.value.defaultChannels.map(c => [c, true])) as Record<string, boolean>
const channelOn = computed(() => fnt[key(ni.value)] ?? defaultChannels())
const note = computed(() => {
  const n = node.value
  if (n.kind === '并行') return '与同列节点并行办理,全部完成后进入下一阶段。'
  if (nodes.value.filter(y => y.col === n.col).length > 1) return '同列存在并行节点,以耗时最长者计入关键路径。'
  return '串行节点 · 完成后自动流转至下一阶段。'
})

const checks = computed(() => {
  const ns = flow.value.nodes
  return [
    { ok: true, text: '起止节点完整' },
    { ok: ns.some(x => x.kind === '审批'), text: '包含召集人审批节点' },
    { ok: ns.some(x => x.kind === '签收'), text: '机构签收节点已配置' },
    { ok: !over.value, text: '关键路径 ' + total.value + ' 天 ' + (over.value ? '>' : '≤') + ' 法定 ' + limit.value + ' 天' },
  ]
})

function pickFlow(name: string) {
  flowName.value = name
  sel.value = 0
}
function setLane(l: string) { lov[key(ni.value)] = l }
function setDays(d: number) { dur[key(ni.value)] = d }
function setTimeout_(i: number) { fot[key(ni.value)] = i }
function toggleChannel(c: string) {
  const cur = channelOn.value
  fnt[key(ni.value)] = { ...cur, [c]: !cur[c] }
}

function publish() {
  if (over.value) {
    say('关键路径超过法定期限 ' + limit.value + ' 个工作日,请调整')
    return
  }
  const version = flow.value.version + 1
  sendAction('A9', 'publishFlowVersion', {
    flow: flow.value.name,
    version,
    total: total.value,
    nodes: nodes.value.map(n => {
      const k = key(n.idx)
      const tIdx = fot[k] ?? (n.kind === '审批' ? 2 : n.kind === '签收' ? 1 : 0)
      const ch = fnt[k] ?? defaultChannels()
      return {
        name: n.name,
        lane: n.lane,
        days: n.days,
        timeoutAction: data.value.timeoutActions[tIdx],
        channels: Object.keys(ch).filter(c => ch[c]),
      }
    }),
  })
  say('已发布 v' + version + ' · 新发布物生效')
}
</script>

<template>
  <section data-screen-label="A9 流程设计器" class="mx-auto flex w-full max-w-[1600px] flex-col gap-3.5 px-8 pt-6 pb-14">
    <PageHeader subtitle="按承办角色分泳道 · 同一列节点并行 · 点击节点编辑时限与超时动作">
      <template #title>
        <span class="tracking-[-0.2px]">流程设计器</span>
        <span class="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] font-semibold text-brand">{{ flow.name }}流程 v{{ flow.version }}</span>
        <span
          :class="cn('rounded-full px-2 py-0.5 text-[11px] font-semibold whitespace-nowrap', over ? 'bg-bad-soft text-bad-ink' : 'bg-ok-soft text-ok-ink')"
        >{{ over ? '超出法定期限' : '校验通过' }}</span>
      </template>
      <div class="flex rounded-[10px] bg-line-2 p-[3px]">
        <button
          v-for="f in data.flows"
          :key="f.name"
          type="button"
          :aria-pressed="f.name === flow.name"
          :class="cn(
            'cursor-pointer rounded-lg px-4 py-1.5 text-[13px] font-medium whitespace-nowrap',
            f.name === flow.name ? 'bg-white text-ink-1 shadow-[0_1px_3px_rgba(15,23,42,.1)]' : 'text-ink-4',
          )"
          @click="pickFlow(f.name)"
        >{{ f.name }}</button>
      </div>
      <Button class="h-9" @click="publish">发布新版本</Button>
    </PageHeader>

    <div class="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-4">
      <div class="flex min-w-0 flex-col gap-3.5">
        <FlowCanvas
          :nodes="nodes"
          :lanes="usedLanes"
          :stages="data.stages"
          :cols="cols"
          :selected="ni"
          @select="i => (sel = i)"
        />
        <FlowGantt :nodes="nodes" :cols="cols" :total="total" :limit="limit" :selected="ni" />
      </div>
      <div class="sticky top-(--sticky-panel) flex flex-col gap-3">
        <NodePanel
          :node="node"
          :note="note"
          :lanes="data.lanes"
          :timeout-actions="data.timeoutActions"
          :timeout="timeoutIdx"
          :channels="data.channels"
          :channel-on="channelOn"
          @lane="setLane"
          @days="setDays"
          @timeout="setTimeout_"
          @channel="toggleChannel"
        />
        <FlowChecks :checks="checks" :versions="data.versions" />
      </div>
    </div>
  </section>
</template>
