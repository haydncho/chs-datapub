<script setup lang="ts">
import { confirm } from '@/lib/confirm'
import { computed, onMounted, ref, watch } from 'vue'
import { flowApi } from '@/api'
import type { FlowNode, FlowTemplateRow } from '@/api/types'
import Chip from '@/components/shared/Chip.vue'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/**
 * A9 流程设计器：模板带版本号；点击节点配置处理人、单人 / 会签 / 或签、时限、超时升级。
 * 「召集人审批」为必经节点（不可删除、不可跳过，驳回固定退回「分析成稿」）。
 */
const page = pageDef('A9')!
const templates = ref<FlowTemplateRow[]>([])
const tplId = ref<number | null>(null)
const tpl = ref<FlowTemplateRow | null>(null)
const nodes = ref<FlowNode[]>([])
const sel = ref(5)
const busy = ref(false)

async function loadList() {
  templates.value = await flowApi.list()
  if (tplId.value == null) tplId.value = templates.value[0]?.id ?? null
}
async function loadTpl() {
  if (tplId.value == null) return
  const r = await flowApi.get(tplId.value)
  tpl.value = r.template
  nodes.value = r.nodes
  if (!nodes.value.some((n) => n.idx === sel.value)) sel.value = nodes.value.find((n) => n.gate)?.idx ?? 1
}
onMounted(() => loadList().then(loadTpl).catch(notifyError))
watch(tplId, () => loadTpl().catch(notifyError))

const nd = computed(() => nodes.value.find((n) => n.idx === sel.value))
const label = (t: FlowTemplateRow) => `${t.kind} ${t.version}`

async function update(body: { mode?: string; days?: number }) {
  if (!tplId.value || !nd.value) return
  try {
    const n = await flowApi.updateNode(tplId.value, nd.value.idx, body)
    nodes.value = nodes.value.map((x) => (x.idx === n.idx ? n : x))
  } catch (e) {
    notifyError(e)
  }
}

async function remove() {
  if (!tplId.value || !nd.value) return
  if (!(await confirm({ title: '删除节点', body: `确定删除节点「${nd.value.name}」?后续节点顺序将自动调整。`, okText: '删除', danger: true }))) return
  try {
    notify((await flowApi.deleteNode(tplId.value, nd.value.idx)).message)
    await loadTpl()
  } catch (e) {
    notifyError(e)
  }
}

async function saveVersion() {
  if (!tplId.value) return
  busy.value = true
  try {
    notify((await flowApi.saveVersion(tplId.value)).message)
    await loadList()
    await loadTpl()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page">
      <template #actions>
        <Button class="px-4 py-[7px]" :disabled="busy" data-testid="save-version" @click="saveVersion">保存为新版本</Button>
      </template>
    </PageHeader>

    <div class="mt-5 grid grid-cols-4 gap-3.5" data-testid="a9-kpis">
      <KpiCard label="流程模板" :value="String(templates.length)" unit="套" icon="flow" tone="primary" desc="月 / 季 / 年 / 专题 / 预警 / 更正" />
      <KpiCard label="当前模板节点" :value="String(nodes.length)" unit="个" icon="grip" tone="ai" :spark="nodes.map((n) => n.days)" desc="柱高为各节点时限(天)" />
      <KpiCard label="审批关口" :value="String(nodes.filter((n) => n.gate).length)" unit="个" icon="shield" tone="warning" desc="召集人审批,未批准不外发" />
      <KpiCard label="总时限" :value="String(nodes.reduce((n, x) => n + x.days, 0))" unit="天" icon="clock" tone="success" desc="各节点时限合计" />
    </div>

    <div class="mt-3.5 flex flex-wrap items-center gap-2 rounded-2xl border border-line-soft bg-surface shadow-card px-[18px] py-3">
      <span class="text-[12px] text-ink-muted">流程模板</span>
      <Chip v-for="t in templates" :key="t.id" :on="t.id === tplId" @click="tplId = t.id">{{ label(t) }}</Chip>
      <Tag v-if="tpl?.pendingVersion" tone="warning" class="ml-auto">{{ tpl.pendingVersion }} 待召集人确认</Tag>
    </div>

    <div class="mt-3.5 grid grid-cols-[minmax(0,1fr)_320px] items-start gap-3.5">
      <div class="rounded-2xl border border-line-soft bg-surface shadow-card bg-[radial-gradient(var(--c-border)_1px,transparent_1px)] bg-size-[16px_16px] p-[18px]">
        <div class="mb-3 text-[12px] text-ink-sub">{{ tpl ? label(tpl) : '' }} · {{ nodes.length }} 个节点 · 点击节点配置</div>
        <div class="grid grid-cols-5 gap-x-[22px] gap-y-7" data-testid="flow-nodes">
          <button
            v-for="(n, i) in nodes"
            :key="n.idx"
            type="button"
            class="relative cursor-pointer rounded-[10px] border-[1.5px] px-3 py-2.5 text-left transition-colors"
            :class="
              n.idx === sel ? 'border-primary bg-primary-tint' : n.gate ? 'border-danger-line bg-danger-soft hover:border-danger' : 'border-line bg-surface hover:border-primary'
            "
            :data-node="n.name"
            @click="sel = n.idx"
          >
            <div class="flex justify-between text-[11px] text-ink-faint"><span>节点 {{ n.idx }}</span><span>{{ n.mode }}</span></div>
            <div class="mt-0.5 text-[13px] font-semibold text-ink">{{ n.name }}</div>
            <div class="mt-0.5 text-[11px] text-ink-muted">{{ n.handler }}</div>
            <div class="text-[11px] text-ink-muted">时限 {{ n.days }} 个工作日</div>
            <span v-if="n.gate" class="absolute -top-[9px] right-2 rounded-full bg-danger-solid px-2 text-[10px] leading-[18px] text-white">必经 · 未批准不外发</span>
            <span v-if="i < nodes.length - 1" class="absolute top-1/2 -right-[17px] -mt-[9px] text-ink-ghost">→</span>
          </button>
        </div>
        <div class="mt-4 flex gap-4 text-[11px] text-ink-muted">
          <span>↺ 召集人审批驳回 → 「分析成稿」</span><span>∥ 专家组审核与“机构同步核对”并行</span><span>签收查阅、意见申诉由机构处理</span>
        </div>
      </div>

      <Panel v-if="nd" :title="`节点配置 · ${nd.name}`">
        <div class="flex flex-col gap-3 text-[12px]">
          <div><div class="mb-1 text-ink-muted">处理人</div><div class="rounded-lg border border-line px-2.5 py-1.5 text-ink">{{ nd.handler }}</div></div>
          <div>
            <div class="mb-1 text-ink-muted">处理方式</div>
            <div v-if="nd.mode === '—'" class="text-ink-faint">机构处理节点,不设处理方式</div>
            <div v-else class="flex gap-1.5">
              <Chip v-for="m in ['单人', '会签', '或签']" :key="m" :on="nd.mode === m" :disabled="nd.gate && m !== '单人'" @click="update({ mode: m })">{{ m }}</Chip>
            </div>
            <div class="mt-1 text-ink-faint">会签:全部同意才通过 · 或签:任一人处理即可</div>
          </div>
          <div>
            <div class="mb-1 text-ink-muted">处理时限</div>
            <div class="flex items-center gap-2">
              <div class="flex items-center overflow-hidden rounded-lg border border-line">
                <button type="button" class="cursor-pointer border-r border-line px-2.5 py-[3px] hover:bg-hover disabled:opacity-40" :disabled="nd.days <= 1" aria-label="减少" @click="update({ days: nd.days - 1 })">−</button>
                <span class="px-3.5 py-[3px] font-semibold" data-testid="node-days">{{ nd.days }}</span>
                <button type="button" class="cursor-pointer border-l border-line px-2.5 py-[3px] hover:bg-hover disabled:opacity-40" :disabled="nd.days >= 30" aria-label="增加" @click="update({ days: nd.days + 1 })">+</button>
              </div>
              <span class="text-ink-sub">个工作日</span>
            </div>
          </div>
          <div><div class="mb-1 text-ink-muted">超时升级</div><div class="rounded-lg border border-line px-2.5 py-1.5 text-ink">超时 1 日提醒 → 2 日升级至 {{ nd.escalateTo }}</div></div>
          <div v-if="nd.gate" class="rounded-lg border border-danger-line bg-danger-soft px-2.5 py-2 text-danger-ink">必经节点:不可删除、不可跳过。驳回固定退回“分析成稿”。</div>
          <div class="flex justify-between border-t border-divider pt-2.5">
            <button type="button" class="cursor-pointer text-danger" data-testid="delete-node" @click="remove">删除节点</button>
            <span class="text-ink-muted">版本记录:{{ tpl?.version }} · {{ tpl?.updatedOn }} · {{ tpl?.updatedBy }}</span>
          </div>
        </div>
      </Panel>
    </div>
  </div>
</template>
