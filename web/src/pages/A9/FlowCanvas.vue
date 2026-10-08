<script setup lang="ts">
import { computed, ref } from 'vue'
import { cn } from '@/lib/utils'
import type { A9Kind } from '@/mock/A9'
import { CW, COLS, KIND, LANE_H, PALETTE, laneTone, type ColSpan, type FlowNode } from './model'
import { vPress } from '@/lib/a11y'

const props = defineProps<{
  nodes: FlowNode[]
  lanes: string[]
  stages: string[]
  cols: ColSpan[]
  selected: number
}>()
const emit = defineEmits<{ select: [i: number]; add: [p: { kind: A9Kind; col?: number; lane?: string }] }>()

const DND = 'application/x-a9-kind'
const dropOn = ref(false)
function dragStart(e: DragEvent, kind: A9Kind) {
  // a finger drag is handled by the pointer handlers below
  if (press) {
    e.preventDefault()
    return
  }
  e.dataTransfer?.setData(DND, kind)
  e.dataTransfer?.setData('text/plain', kind)
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy'
}
function dragOver(e: DragEvent) {
  if (!e.dataTransfer?.types.includes(DND)) return
  e.preventDefault()
  e.dataTransfer.dropEffect = 'copy'
  dropOn.value = true
}
/** the drop point picks the stage column and the swimlane */
function drop(e: DragEvent) {
  dropOn.value = false
  const kind = e.dataTransfer?.getData(DND) as A9Kind | undefined
  if (!kind) return
  e.preventDefault()
  dropAt(e.currentTarget as HTMLElement, kind, e.clientX, e.clientY)
}
function dropAt(el: HTMLElement, kind: A9Kind, x: number, y: number) {
  const box = el.getBoundingClientRect()
  const col = Math.max(0, Math.min(COLS - 1, Math.floor(((x - box.left) / box.width) * COLS)))
  const lane = props.lanes[Math.max(0, Math.min(props.lanes.length - 1, Math.floor((y - box.top) / LANE_H)))]
  emit('add', { kind, col, lane })
}

/**
 * Touch / pen: HTML5 drag-and-drop does not start from a finger, so a palette chip is dragged with
 * pointer events instead — a ghost chip follows the finger and lifting it over the canvas drops the
 * node there (same placement rule as the mouse drop). A plain tap still adds after the selected node.
 */
const ghost = ref<{ kind: A9Kind; label: string; x: number; y: number } | null>(null)
let press: { kind: A9Kind; label: string; x: number; y: number } | null = null
let swallowClick = false
const canvasAt = (x: number, y: number) =>
  (document.elementFromPoint(x, y)?.closest('[data-a9-canvas]') as HTMLElement | null) ?? null
function pDown(e: PointerEvent, kind: A9Kind, label: string) {
  if (e.pointerType === 'mouse') return
  press = { kind, label, x: e.clientX, y: e.clientY }
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
}
function pMove(e: PointerEvent) {
  if (!press) return
  if (!ghost.value && Math.hypot(e.clientX - press.x, e.clientY - press.y) < 8) return
  ghost.value = { kind: press.kind, label: press.label, x: e.clientX, y: e.clientY }
  dropOn.value = !!canvasAt(e.clientX, e.clientY)
}
function pUp(e: PointerEvent) {
  const g = ghost.value
  press = null
  ghost.value = null
  dropOn.value = false
  if (!g) return
  swallowClick = true
  setTimeout(() => (swallowClick = false), 400)
  const el = canvasAt(e.clientX, e.clientY)
  if (el) dropAt(el, g.kind, e.clientX, e.clientY)
}
function pCancel() {
  press = null
  ghost.value = null
  dropOn.value = false
}
function tapAdd(kind: A9Kind) {
  if (swallowClick) {
    swallowClick = false
    return
  }
  emit('add', { kind })
}

const ny = (n: FlowNode) => props.lanes.indexOf(n.lane) * LANE_H + 14
const pct = (v: number) => v.toFixed(2) + '%'

const canvasH = computed(() => props.lanes.length * LANE_H)
const laneLines = computed(() => props.lanes.slice(0, -1).map((_, i) => (i + 1) * LANE_H - 1))
const colLines = [1, 2, 3, 4, 5].map(c => pct(c * CW))

const laneRows = computed(() =>
  props.lanes.map(l => ({ n: l, c: props.nodes.filter(x => x.lane === l).length })),
)

/** orthogonal connectors between consecutive stage columns (fork / join) */
const edges = computed(() => {
  const out: { key: string; x1: string; xm: string; x2: string; y1: number; y2: number; on: boolean }[] = []
  for (const a of props.nodes) {
    for (const b of props.nodes) {
      if (b.col !== a.col + 1) continue
      out.push({
        key: a.idx + '-' + b.idx,
        x1: pct((a.col + 0.93) * CW),
        x2: pct((b.col + 0.07) * CW),
        xm: pct((a.col + 1) * CW),
        y1: ny(a) + 34,
        y2: ny(b) + 34,
        on: a.idx === props.selected || b.idx === props.selected,
      })
    }
  }
  // highlighted links on top
  return out.sort((p, q) => Number(p.on) - Number(q.on))
})
</script>

<template>
  <div class="overflow-hidden rounded-[var(--radius-card)] border border-line-1 bg-white">
    <!-- node palette -->
    <div class="flex flex-wrap items-center gap-2 border-b border-line-2 bg-[#FAFBFD] px-3.5 py-2.5">
      <span class="mr-1 text-xs whitespace-nowrap text-ink-4">节点库</span>
      <button type="button"
        v-for="p in PALETTE"
        :key="p.kind"
        draggable="true"
        data-drag
        :aria-label="'添加' + p.label"
        class="flex cursor-grab items-center gap-1.5 rounded-lg border border-dashed border-[#C9D3E1] bg-white px-2.5 py-[5px] text-xs whitespace-nowrap select-none hover:border-brand hover:text-brand max-xl:min-h-10 max-xl:px-3"
        @click="tapAdd(p.kind)"
        @dragstart="dragStart($event, p.kind)"
        @pointerdown="pDown($event, p.kind, p.label)"
        @pointermove="pMove"
        @pointerup="pUp"
        @pointercancel="pCancel"
      >
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" :style="{ stroke: KIND[p.kind].c }" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="KIND[p.kind].icon" /></svg>
        {{ p.label }}
      </button>
      <div class="flex-1" />
      <span class="text-xs whitespace-nowrap text-ink-5 max-xl:whitespace-normal">点击添加到所选节点之后 · 或拖入画布的泳道<span class="lg:hidden"> · 画布可左右滑动</span></span>
    </div>

    <div class="max-xl:overflow-x-auto">
    <div class="grid grid-cols-[132px_minmax(0,1fr)] max-xl:min-w-[880px] max-lg:grid-cols-[120px_minmax(0,1fr)]">
      <div class="flex h-11 items-center border-r border-b border-line-2 bg-surface-1 px-3.5 text-[11px] font-semibold tracking-[.5px] text-ink-5 max-xl:sticky max-xl:left-0 max-xl:z-[4] max-lg:px-3">承办角色</div>
      <div class="grid h-11 border-b border-line-2" :style="{ gridTemplateColumns: `repeat(${COLS},1fr)` }">
        <div
          v-for="(l, c) in stages"
          :key="l"
          :class="cn('flex items-center justify-between border-l px-3 text-xs whitespace-nowrap', c ? 'border-line-2' : 'border-transparent')"
        >
          <span class="font-medium text-ink-3">阶段 {{ c + 1 }} · {{ l }}</span>
          <span :class="cn('yb-num font-semibold', cols[c]?.has ? 'text-brand' : 'text-ink-6')">{{ cols[c]?.has ? 'D+' + cols[c]!.end : '—' }}</span>
        </div>
      </div>

      <!-- swimlane headers -->
      <div class="flex flex-col border-r border-line-2 bg-surface-1 max-xl:sticky max-xl:left-0 max-xl:z-[4] max-lg:shadow-[6px_0_10px_-6px_rgba(15,23,42,.18)]">
        <div v-for="l in laneRows" :key="l.n" class="flex h-24 items-center gap-2.5 border-b border-line-2 px-3.5 max-lg:gap-2 max-lg:px-3">
          <span :class="cn('flex size-7 shrink-0 items-center justify-center rounded-lg text-xs font-bold', laneTone(l.n))">{{ l.n[0] }}</span>
          <div class="min-w-0 leading-[1.3]">
            <div class="text-xs font-semibold whitespace-nowrap">{{ l.n }}</div>
            <div class="text-[11px] whitespace-nowrap text-ink-5">{{ l.c }} 个节点</div>
          </div>
        </div>
      </div>

      <!-- canvas -->
      <div
        data-a9-canvas
        :class="cn('relative bg-[radial-gradient(#E3E8F0_1px,transparent_1px)] bg-[size:16px_16px]', dropOn && 'outline-2 outline-dashed outline-brand')"
        :style="{ height: canvasH + 'px' }"
        @dragover="dragOver"
        @dragleave="dropOn = false"
        @drop="drop"
      >
        <div v-for="x in colLines" :key="x" class="absolute inset-y-0 border-l border-dashed border-line-2" :style="{ left: x }" />
        <div v-for="y in laneLines" :key="y" class="absolute inset-x-0 border-t border-line-2" :style="{ top: y + 'px' }" />
        <svg class="pointer-events-none absolute inset-0 size-full overflow-visible">
          <defs>
            <marker id="a9Arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10z" fill="#98A2B3" /></marker>
            <marker id="a9ArrOn" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10z" :style="{ fill: 'var(--brand)' }" /></marker>
          </defs>
          <g v-for="e in edges" :key="e.key" :style="{ stroke: e.on ? 'var(--brand)' : '#C3CAD6' }" stroke-width="1.5">
            <line :x1="e.x1" :y1="e.y1" :x2="e.xm" :y2="e.y1" />
            <line :x1="e.xm" :y1="e.y1" :x2="e.xm" :y2="e.y2" />
            <line :x1="e.xm" :y1="e.y2" :x2="e.x2" :y2="e.y2" :marker-end="e.on ? 'url(#a9ArrOn)' : 'url(#a9Arr)'" />
          </g>
        </svg>
        <div v-press
          v-for="n in nodes"
          :key="n.idx"
          :aria-label="n.name + ' · ' + n.kind + ' · ' + (n.days ? n.days + ' 天' : '即时')"
          :aria-pressed="n.idx === selected"
          :class="cn(
            'absolute z-[2] flex h-[68px] cursor-pointer overflow-hidden rounded-[10px] border-[1.5px] bg-white hover:shadow-[0_4px_14px_rgba(15,23,42,.10)]',
            n.kind === '可选' ? 'border-dashed' : 'border-solid',
            n.idx === selected
              ? 'border-brand shadow-[0_0_0_3px_color-mix(in_srgb,var(--brand)_16%,white)]'
              : 'border-line-1 shadow-[0_1px_2px_rgba(15,23,42,.05)]',
          )"
          :style="{ left: pct(n.col * CW + CW * 0.07), top: ny(n) + 'px', width: pct(CW * 0.86) }"
          @click="emit('select', n.idx)"
        >
          <span class="w-1 shrink-0" :style="{ background: KIND[n.kind].c }" />
          <div class="flex min-w-0 flex-1 flex-col justify-between px-2.5 py-2">
            <div class="flex items-center gap-1.5">
              <span class="flex size-5 shrink-0 items-center justify-center rounded-md" :style="{ background: KIND[n.kind].cb }">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" :style="{ stroke: KIND[n.kind].c }" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path :d="KIND[n.kind].icon" /></svg>
              </span>
              <span class="truncate text-[13px] font-semibold">{{ n.name }}</span>
            </div>
            <div class="flex items-center justify-between text-[11px] whitespace-nowrap">
              <span class="font-medium" :style="{ color: KIND[n.kind].c }">{{ n.kind }}</span>
              <span class="yb-num font-semibold text-ink-3">{{ n.days ? n.days + ' 天' : '即时' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    </div>
    <div
      v-if="ghost"
      class="pointer-events-none fixed z-[90] flex -translate-x-1/2 -translate-y-[130%] items-center gap-1.5 rounded-lg border border-brand bg-white px-3 py-2 text-xs font-medium text-brand shadow-[0_8px_24px_rgba(15,23,42,.18)]"
      :style="{ left: ghost.x + 'px', top: ghost.y + 'px' }"
      aria-hidden="true"
    >
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" :style="{ stroke: KIND[ghost.kind].c }" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="KIND[ghost.kind].icon" /></svg>
      {{ ghost.label }}
    </div>
  </div>
</template>
