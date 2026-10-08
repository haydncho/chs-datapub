<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { appearance } from '@/app/appearance'
import PanelTitle from './PanelTitle.vue'
import { useCockpit } from './store'

/**
 * 实时提醒(主屏下排、双屏副屏共用):按级别排序,预警 / 提醒函加强显示。
 * 面板位置与宽度由使用方通过 class 传入(高度固定 276px,与下排其他面板对齐)。
 */
const { I, alertsAll } = useCockpit()
const panel = 'absolute top-[780px] flex h-[276px] flex-col rounded-[10px] border border-[rgb(var(--ck-line)/.16)] bg-[rgb(var(--ck-panel)/.66)] px-[22px] py-3.5'

/* ---------- 实时提醒:窗口恰好容纳 WIN 条完整提醒;超过时整条整条地向上滚动,任何一条都不会被剪断 */
const ROW = 52
const WIN = 4
const n = computed(() => alertsAll.value.length)
const rolling = computed(() => n.value > WIN && appearance.mot !== 0)
const rows = computed(() => (rolling.value ? [...alertsAll.value, ...alertsAll.value.slice(0, WIN)] : alertsAll.value))
const idx = ref(0)
const smooth = ref(true)
const paused = ref(false)
let iv: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  iv = setInterval(async () => {
    if (!rolling.value || paused.value) return
    idx.value++
    if (idx.value >= n.value) { // 滚到复制出来的头部后,无动画跳回起点
      await new Promise(r => setTimeout(r, 620))
      smooth.value = false
      idx.value = 0
      await nextTick()
      requestAnimationFrame(() => (smooth.value = true))
    }
  }, 3600)
})
onBeforeUnmount(() => clearInterval(iv))
watch(() => I.value.id, () => { idx.value = 0 })
const track = computed(() => ({
  transform: `translateY(${-idx.value * ROW}px)`,
  transition: smooth.value ? 'transform .6s cubic-bezier(.2,.8,.2,1)' : 'none',
}))
const winH = computed(() => Math.min(n.value, WIN) * ROW)
/** 窗口里实际显示的是第 start 条起的 WIN 条(回绕到开头时如实写出,如 "3–5、1 / 5") */
const pos = computed(() => {
  const N = n.value
  if (N <= WIN || !rolling.value) return `${N} 条`
  const start = (idx.value % N) + 1
  const end = ((idx.value + WIN - 1) % N) + 1
  if (end >= start) return `${start}–${end} / ${N}`
  return `${start === N ? N : `${start}–${N}`}、${end === 1 ? 1 : `1–${end}`} / ${N}`
})
</script>

<template>
  <!-- 实时提醒:加宽后单行即可读完 -->
  <div :class="panel" data-testid="cockpit-alerts" @mouseenter="paused = true" @mouseleave="paused = false">
    <div class="mb-2 flex items-center justify-between">
      <div class="flex items-center gap-2.5">
        <PanelTitle :title="I.alertTitle" bar="#F5B74E" />
        <span class="relative flex size-2">
          <span class="absolute inline-flex size-full rounded-full bg-[#3FD1A0] [animation:ybRing_1.8s_ease-out_infinite]" />
          <span class="relative inline-flex size-2 rounded-full bg-[#3FD1A0]" />
        </span>
      </div>
      <span class="yb-num text-[15px] text-[#F5B74E]">{{ pos }}</span>
    </div>
    <div class="relative" :class="appearance.mot === 0 && n > WIN ? 'overflow-y-auto' : 'overflow-hidden'" :style="{ height: winH + 'px' }">
      <div :style="track">
        <div
          v-for="(a, i) in rows"
          :key="i"
          class="relative flex items-center gap-3 border-t border-[rgb(var(--ck-line)/.1)] py-1.5 pl-2"
          :style="{ height: ROW + 'px', background: a.hi ? `linear-gradient(90deg, color-mix(in srgb,${a.c} 12%,transparent), transparent 70%)` : 'transparent' }"
        >
          <span v-if="a.hi" class="absolute top-1.5 bottom-1.5 left-0 w-[3px] rounded-[2px]" :style="{ background: a.c, boxShadow: `0 0 8px ${a.c}` }" />
          <span
            class="w-[60px] shrink-0 rounded-[3px] border py-px text-center text-sm whitespace-nowrap"
            :style="a.hi ? { borderColor: a.c, background: a.c, color: '#04101F', fontWeight: 600 } : { borderColor: a.c, color: a.c }"
          >{{ a.ty }}</span>
          <span :class="['line-clamp-2 flex-1 text-[15px] leading-[1.3]', a.hi ? 'font-medium text-[#F4F8FF]' : 'text-[#C9D6EA]']" :title="a.txt">{{ a.txt }}</span>
          <span class="yb-num shrink-0 text-sm text-[#6F84A6]">{{ a.t }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
