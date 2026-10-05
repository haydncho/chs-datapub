<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { appearance } from '@/app/appearance'
import AnimatedNumber from './AnimatedNumber.vue'
import PanelTitle from './PanelTitle.vue'
import { TONE_C, useCockpit } from './store'

const { s, I, bullets, alertsAll } = useCockpit()
const panel = 'rounded-[10px] border border-[rgba(90,150,255,.16)] bg-[rgba(10,22,46,.66)] px-[22px] py-4'

/* ---------- 实时提醒:窗口恰好容纳 WIN 条完整提醒;超过时整条整条地向上滚动,任何一条都不会被剪断 */
const ROW = 58
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
const pos = computed(() => (n.value > WIN ? `${(idx.value % n.value) + 1}–${Math.min(n.value, (idx.value % n.value) + WIN)} / ${n.value}` : `${n.value} 条`))
</script>

<template>
  <div class="absolute top-[216px] left-[1444px] flex h-[684px] w-[448px] flex-col gap-4">
    <!-- 效 -->
    <div :class="[panel, 'h-[204px] shrink-0']">
      <div class="mb-2 flex items-center justify-between">
        <PanelTitle :title="I.effTitle" bar="#5B8FD9" />
        <span class="text-sm text-[#6F84A6]">目标带 0.95–1.05</span>
      </div>
      <div v-for="b in bullets" :key="b.k" class="mb-1.5 last:mb-0">
        <div class="flex items-center justify-between gap-2">
          <span class="truncate text-base text-[#C9D6EA]">{{ b.k }}</span>
          <span class="flex shrink-0 items-center gap-2">
            <span class="rounded px-1.5 py-px text-xs" :style="{ color: b.c, background: b.c + '22' }">{{ b.st }}</span>
            <AnimatedNumber :value="b.v" :replay="s.pulse" class="yb-num w-[56px] text-right text-[22px] font-semibold" :style="{ color: b.c }" />
          </span>
        </div>
        <div class="relative mt-1 h-2.5 rounded-[3px] bg-[rgba(255,255,255,.06)]">
          <div class="absolute inset-y-0 left-[41.7%] w-[16.6%] bg-[rgba(63,209,160,.22)]" />
          <div class="absolute -top-[3px] -bottom-[3px] left-1/2 border-l border-[rgba(230,238,249,.5)]" />
          <div
            class="absolute -top-[5px] -ml-0.5 h-[20px] w-[5px] rounded-[2px] transition-[left] duration-[1100ms] ease-[cubic-bezier(.2,.8,.2,1)]"
            :style="{ left: s.intro ? b.p : '50%', background: b.c, boxShadow: `0 0 10px ${b.c}` }"
          />
        </div>
      </div>
    </div>

    <!-- 错 -->
    <div :class="[panel, 'h-[140px] shrink-0']">
      <div class="mb-2.5"><PanelTitle :title="I.errTitle" bar="#FF6B5E" /></div>
      <div class="grid grid-cols-4 gap-2">
        <div
          v-for="e in I.errs"
          :key="e.label"
          class="relative overflow-hidden rounded-md border border-[rgba(90,150,255,.1)] bg-[rgba(255,255,255,.03)] px-2.5 pt-2 pb-1.5"
        >
          <span class="absolute inset-x-0 top-0 h-[2px]" :style="{ background: TONE_C[e.tone] }" />
          <div class="truncate text-[13px] text-[#9FB2D1]">{{ e.label }}</div>
          <div class="whitespace-nowrap">
            <AnimatedNumber :value="e.value" :replay="s.pulse" class="yb-num text-[26px] leading-tight font-semibold" :style="{ color: TONE_C[e.tone] }" />
            <span class="text-xs text-[#6F84A6]"> {{ e.unit }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 实时提醒 -->
    <div :class="[panel, 'flex min-h-0 flex-1 flex-col']" data-testid="cockpit-alerts" @mouseenter="paused = true" @mouseleave="paused = false">
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
            class="flex items-start gap-2.5 border-t border-[rgba(90,150,255,.1)] py-[7px]"
            :style="{ height: ROW + 'px' }"
          >
            <span class="mt-0.5 rounded-[3px] border px-[7px] py-px text-xs whitespace-nowrap" :style="{ borderColor: a.c, color: a.c }">{{ a.ty }}</span>
            <span class="line-clamp-2 flex-1 text-[15px] leading-[1.35] text-[#DDE6F3]" :title="a.txt">{{ a.txt }}</span>
            <span class="yb-num text-sm text-[#6F84A6]">{{ a.t }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
