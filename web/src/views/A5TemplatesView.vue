<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { templateApi } from '@/api'
import type { ChartTemplate, ReportBlock, ReportPreset } from '@/api/types'
import Chip from '@/components/shared/Chip.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import SegTabs from '@/components/shared/SegTabs.vue'
import { Button } from '@/components/ui/button'
import StrokeIcon from '@/components/ui/stroke-icon/StrokeIcon.vue'
import { iconPaths } from '@/lib/icons'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/**
 * A5 图表与报告模板：图表模板库（统一图表规范）+ 报告拼装器（拖拽或点 + 添加区块，可上移、删除；生成后进入发布工作流第 3 步）。
 */
const page = pageDef('A5')!
const tab = ref<'lib' | 'asm'>('asm')

const charts = ref<ChartTemplate[]>([])
const blocks = ref<ReportBlock[]>([])
const presets = ref<ReportPreset[]>([])
const tplSel = ref(0)
const preset = ref('')
const canvas = ref<number[]>([])
const dragging = ref<number | null>(null)
const over = ref(false)
const busy = ref(false)

onMounted(async () => {
  try {
    ;[charts.value, blocks.value, presets.value] = await Promise.all([templateApi.charts(), templateApi.blocks(), templateApi.presets()])
    tplSel.value = Math.max(0, charts.value.findIndex((c) => c.name === '气泡全景'))
    applyPreset(presets.value[0])
  } catch (e) {
    notifyError(e)
  }
})

function applyPreset(p?: ReportPreset) {
  if (!p) return
  preset.value = p.name
  canvas.value = [...p.blockIds]
}

const blockById = computed(() => new Map(blocks.value.map((b) => [b.id, b])))
const pages = computed(() => Math.max(1, Math.round(canvas.value.reduce((a, id) => a + (blockById.value.get(id)?.height ?? 0), 0) / 90)))
const sel = computed(() => charts.value[tplSel.value])

function add(id: number) {
  canvas.value = [...canvas.value, id]
}
function up(k: number) {
  if (!k) return
  const c = [...canvas.value]
  ;[c[k - 1], c[k]] = [c[k], c[k - 1]]
  canvas.value = c
}
function del(k: number) {
  canvas.value = canvas.value.filter((_, j) => j !== k)
}
function onDragStart(e: DragEvent, id: number) {
  dragging.value = id
  e.dataTransfer?.setData('text/plain', String(id))
}
function onDrop(e: DragEvent) {
  over.value = false
  const id = dragging.value ?? Number(e.dataTransfer?.getData('text/plain'))
  if (Number.isFinite(id) && blockById.value.has(id)) add(id)
  dragging.value = null
}

async function generate() {
  busy.value = true
  try {
    notify((await templateApi.draft(preset.value, canvas.value)).message)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

/** 模板卡缩略图标（示意，图表本身由图表库按规范绘制）。 */
const glyph: Record<string, string> = {
  分位条: 'M3 12h18M9 8v8M15 10v4', 趋势线: 'M3 17l5-5 4 3 8-8', 结构堆叠条: 'M3 7h10v4H3zM13 7h8v4h-8zM3 13h6v4H3zM9 13h12v4H9z',
  分组柱: 'M5 20V10M10 20V6M15 20v-8M20 20V4', 排行条: 'M3 6h16M3 11h12M3 16h8', 气泡全景: 'M7 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM16 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM17 19a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  散点: 'M5 18h.01M8 13h.01M12 15h.01M15 9h.01M19 6h.01M10 8h.01', 归因瀑布: 'M4 20V12M9 12V8M14 8V5M19 20V5',
  流向图: 'M4 12c5 0 5-6 10-6h6M4 12c5 0 5 6 10 6h6', 对比表: 'M3 5h18v14H3zM3 10h18M12 5v14',
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page">
      <template #actions>
        <SegTabs v-model="tab" :items="[{ value: 'lib', label: '图表模板库' }, { value: 'asm', label: '报告拼装器' }]" />
      </template>
    </PageHeader>

    <!-- 图表模板库 -->
    <div v-if="tab === 'lib'" class="mt-5 grid grid-cols-[minmax(0,1fr)_300px] items-start gap-4">
      <div class="grid grid-cols-5 gap-3" data-testid="chart-templates">
        <button
          v-for="(t, i) in charts"
          :key="t.id"
          type="button"
          class="cursor-pointer rounded-[10px] border-[1.5px] p-3 text-left transition-colors hover:border-primary"
          :class="i === tplSel ? 'border-primary bg-primary-tint' : 'border-line bg-surface'"
          @click="tplSel = i"
        >
          <div class="mb-2 flex h-10 items-center justify-center rounded-md bg-subtle text-primary">
            <StrokeIcon :d="glyph[t.name] ?? iconPaths.rpt" :size="22" />
          </div>
          <div class="text-[13px] font-semibold text-ink">{{ t.name }}</div>
          <div class="mt-0.5 text-[12px] text-ink-muted">{{ t.useCase }}</div>
          <div class="mt-1.5 text-[11px] text-ink-faint">{{ t.usedBy }} 个指标使用</div>
        </button>
      </div>
      <Panel v-if="sel" :title="sel.name">
        <div class="grid grid-cols-[64px_1fr] gap-2 text-[12px]">
          <span class="text-ink-muted">适用</span><span>{{ sel.useCase }}</span>
          <span class="text-ink-muted">示例指标</span><span>{{ sel.example }}</span>
          <span class="text-ink-muted">适用档位</span><span :class="sel.tierScope.startsWith('仅具名') ? 'font-semibold text-warning' : ''">{{ sel.tierScope }}</span>
          <span class="text-ink-muted">版本</span><span>{{ sel.version }} · {{ sel.updated }} 更新</span>
        </div>
        <div class="mt-3 rounded-lg bg-subtle px-2.5 py-2 text-[12px] text-ink-muted">
          图表规范:不使用 3D 与渐变;状态色固定为绿 = 结余 / 正常、橙 = 关注、红 = 逆差 / 预警。
        </div>
      </Panel>
    </div>

    <!-- 报告拼装器 -->
    <div v-else class="mt-5 overflow-hidden rounded-[10px] border border-line bg-surface">
      <div class="flex flex-wrap items-center gap-2 border-b border-divider px-4 py-3">
        <span class="text-[12px] text-ink-muted">预置模板</span>
        <Chip v-for="p in presets" :key="p.name" :on="p.name === preset" @click="applyPreset(p)">{{ p.name }}</Chip>
        <div class="flex-1" />
        <Button size="sm" :disabled="busy || !canvas.length" data-testid="gen-report" @click="generate">生成报告并提交发布工作流</Button>
      </div>
      <div class="grid min-h-[520px] grid-cols-[240px_minmax(0,1fr)]">
        <div class="border-r border-divider p-3">
          <div class="mb-2 text-[12px] text-ink-muted">指标区块 · 拖到右侧或点 +</div>
          <div class="flex flex-col gap-1.5" data-testid="blocks">
            <div
              v-for="b in blocks"
              :key="b.id"
              draggable="true"
              class="flex cursor-grab items-center gap-2 rounded-lg border border-line bg-surface px-2.5 py-[7px] text-[12px] hover:border-primary"
              @dragstart="onDragStart($event, b.id)"
            >
              <StrokeIcon :d="iconPaths.grip" :size="14" class="text-ink-ghost" />
              <span class="flex-1 text-ink">{{ b.name }}</span>
              <button type="button" class="cursor-pointer text-[14px] leading-none font-semibold text-primary" :aria-label="`添加 ${b.name}`" @click="add(b.id)">+</button>
            </div>
          </div>
        </div>
        <div
          class="flex justify-center bg-app p-4"
          :class="over ? 'bg-primary-tint' : ''"
          @dragover.prevent="over = true"
          @dragleave="over = false"
          @drop.prevent="onDrop"
        >
          <!-- 公文纸：深色主题下仍白底 -->
          <div data-paper class="flex w-full max-w-[620px] flex-col gap-2 rounded border border-line bg-white px-7 py-6 shadow-[0_4px_18px_rgba(15,23,42,.06)]" data-testid="canvas">
            <div class="flex justify-between text-[11px] text-ink-faint">
              <span>{{ preset }} · 2026年8月</span><span>{{ canvas.length }} 个区块 · 约 {{ pages }} 页</span>
            </div>
            <div v-for="(id, k) in canvas" :key="`${k}-${id}`" class="flex items-center gap-2.5 rounded-lg border border-dashed border-line-strong px-3 py-2.5">
              <span class="w-5 text-[11px] text-ink-faint">{{ k + 1 }}</span>
              <div class="flex-1">
                <div class="text-[13px] font-medium text-ink">{{ blockById.get(id)?.name }}</div>
                <div class="mt-1.5 rounded-[3px] bg-ink-tint" :style="{ height: `${blockById.get(id)?.height ?? 30}px` }" />
              </div>
              <button type="button" class="cursor-pointer text-ink-muted hover:text-primary disabled:opacity-30" :disabled="!k" aria-label="上移" @click="up(k)">
                <StrokeIcon :d="iconPaths.up" :size="14" />
              </button>
              <button type="button" class="cursor-pointer text-danger" aria-label="删除" @click="del(k)"><StrokeIcon :d="iconPaths.x" :size="14" /></button>
            </div>
            <div class="rounded-lg border-[1.5px] border-dashed border-primary-line-strong py-3.5 text-center text-[12px] text-primary">拖放区块到此处</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
