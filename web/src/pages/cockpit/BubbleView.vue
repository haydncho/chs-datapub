<script setup lang="ts">
import { computed, ref } from 'vue'
import { useCockpit } from './store'
import { vPress } from '@/lib/a11y'

const { s, I, bubble, motion, labelOf } = useCockpit()
/* 未常驻标注的气泡,悬停时临时显示名称 */
const hov = ref<string | null>(null)
const hovLabel = computed(() => (hov.value ? labelOf(hov.value) : null))

/**
 * 鼠标命中按「离气泡中心最近」判定(而不是看谁的 DOM 在最上层):两个气泡重叠、或气泡边缘压到
 * 文字时,指向哪个中心就命中哪个,被遮住的气泡中心也能悬停 / 点中。键盘仍可逐个聚焦气泡。
 */
const plot = ref<HTMLDivElement | null>(null)
function hit(e: MouseEvent): string | null {
  const el = plot.value
  if (!el || !s.grow) return null
  const rc = el.getBoundingClientRect()
  const scale = rc.width / (el.offsetWidth || 1)
  let best: string | null = null
  let bestD = Infinity
  for (const b of bubble.value.main) {
    const dx = e.clientX - (rc.left + (b.cx / 100) * rc.width)
    const dy = e.clientY - (rc.top + (b.cy / 100) * rc.height)
    const d = Math.hypot(dx, dy) / (Math.max(b.r, 6) * scale)
    if (d <= 1.1 && d < bestD) { best = b.k; bestD = d }
  }
  return best
}
function onMove(e: MouseEvent) { hov.value = hit(e) }
function onClick(e: MouseEvent) {
  const k = hit(e)
  if (k) s.sel = k
}
</script>

<template>
  <div
    ref="plot"
    class="absolute top-1.5 right-3 bottom-9 left-[72px]"
    :class="hov ? 'cursor-pointer' : ''"
    data-testid="bubble-plot"
    @mousemove="onMove"
    @mouseleave="hov = null"
    @click="onClick"
  >
    <!-- quadrant tints -->
    <div class="absolute inset-x-0 top-0 h-1/2 bg-[linear-gradient(180deg,rgba(255,90,78,.08),rgba(255,90,78,0))]" />
    <div class="absolute inset-x-0 bottom-0 h-1/2 bg-[linear-gradient(0deg,rgba(46,209,138,.07),rgba(46,209,138,0))]" />
    <!-- grid -->
    <div v-for="t in bubble.yT" :key="'y' + t.p" class="absolute inset-x-0 border-t border-[rgba(255,255,255,.06)]" :style="{ top: t.p + '%' }">
      <span class="yb-num absolute right-full -top-[11px] mr-2.5 text-[15px] whitespace-nowrap text-[#6F84A6]">{{ t.label }}</span>
    </div>
    <div v-for="t in bubble.xT" :key="'x' + t.p" class="absolute inset-y-0 border-l border-[rgba(255,255,255,.035)]" :style="{ left: t.p + '%' }">
      <span class="yb-num absolute top-full -left-[30px] mt-2 w-[60px] text-center text-[15px] text-[#6F84A6]">{{ t.label }}</span>
    </div>
    <!-- median lines -->
    <div class="absolute inset-x-0 top-1/2 border-t-[1.5px] border-dashed border-[rgba(230,238,249,.5)]" />
    <div class="absolute inset-y-0 border-l-[1.5px] border-dashed border-[rgb(var(--ck-accl)/.35)]" :style="{ left: bubble.med + '%' }" />
    <span class="absolute bottom-1 ml-2 text-sm text-[rgb(var(--ck-accl))]" :style="{ left: bubble.med + '%' }">病例数中位</span>
    <!-- quadrant labels -->
    <span class="absolute top-2 right-2.5 text-[15px] font-semibold text-[#FF8A7E] pointer-events-none z-[1] rounded bg-[rgb(var(--ck-deep)/.72)] px-1.5">高量 · 逆差 — 关键少数</span>
    <span class="absolute top-2 left-2.5 text-[15px] text-[#C9877F] pointer-events-none z-[1] rounded bg-[rgb(var(--ck-deep)/.72)] px-1.5">低量 · 逆差</span>
    <span class="absolute right-2.5 bottom-2 text-[15px] text-[#6FCDA8] pointer-events-none z-[1] rounded bg-[rgb(var(--ck-deep)/.72)] px-1.5">高量 · 结余</span>
    <span class="absolute bottom-2 left-2.5 text-[15px] text-[#5A9C84] pointer-events-none z-[1] rounded bg-[rgb(var(--ck-deep)/.72)] px-1.5">低量 · 结余</span>
    <!-- city reference (hospital view) -->
    <div
      v-for="(g, i) in bubble.grays"
      :key="'g' + i"
      class="absolute rounded-full border border-[rgba(143,163,196,.26)] bg-[rgba(143,163,196,.14)]"
      :style="g"
    />
    <!-- bubbles -->
    <div v-press
      v-for="b in bubble.main"
      :key="I.id + b.k"
      :aria-label="b.tip"
      :data-k="b.k"
      class="pointer-events-none absolute rounded-full"
      :style="b.style"
      @click="s.sel = b.k"
      @focus="hov = b.k"
      @blur="hov = null"
    />
    <!-- effects: scanning band + pulse rings -->
    <template v-if="motion">
      <div
        class="pointer-events-none absolute inset-y-0 z-[2] w-[14%] border-r-[1.5px] border-[rgb(var(--ck-accl)/.55)] bg-[linear-gradient(90deg,rgb(var(--ck-acc)/0),rgb(var(--ck-acc)/.07)_70%,rgb(var(--ck-acc)/.22))] [animation:cockScan_7s_linear_infinite]"
      />
      <div
        v-for="p in bubble.pulses"
        :key="'p' + p.k"
        class="pointer-events-none absolute z-[55] rounded-full"
        :style="p.style"
      />
    </template>
    <!-- labels -->
    <div
      v-for="l in bubble.labels"
      :key="'l' + l.k"
      class="pointer-events-none absolute z-[80] rounded px-2 py-px text-[15px] font-semibold whitespace-nowrap text-[#F4F8FF] bg-[rgb(var(--ck-deep)/.8)]"
      :style="l.style"
    >{{ l.t }}</div>
    <div
      v-if="hovLabel"
      class="pointer-events-none absolute z-[85] rounded border border-[rgb(var(--ck-accl)/.35)] px-2 py-px text-[15px] font-semibold whitespace-nowrap text-[#F4F8FF] bg-[rgb(var(--ck-deep)/.88)]"
      :style="hovLabel.style"
    >{{ hovLabel.t }}</div>
  </div>
  <span class="absolute right-[56px] bottom-0 text-sm text-[#6F84A6]">{{ I.xLabel }} →</span>
  <span class="absolute top-0 left-0 text-sm text-[#6F84A6] [writing-mode:vertical-rl]">例均基金差额(元)</span>
</template>
