<script setup lang="ts">
import { computed } from 'vue'
import AnimatedNumber from './AnimatedNumber.vue'
import PanelTitle from './PanelTitle.vue'
import { K, useCockpit } from './store'

/* 气泡图右侧:钱 / 效 / 错 上下排列,三块等高,合计高度与气泡图区域一致 */
const { s, I, months, bullets, errs } = useCockpit()
const balC = computed(() => (I.value.money.balance.startsWith('−') ? K.red : K.green))
const panel = 'flex min-h-0 flex-1 flex-col rounded-[10px] border border-[rgb(var(--ck-line)/.16)] bg-[rgb(var(--ck-panel)/.66)] px-[22px] py-3.5'
const moneyRows = computed(() => [
  { l: I.value.money.m1, v: I.value.money.budget, c: 'rgb(var(--ck-accl))' },
  { l: I.value.money.m2, v: I.value.money.spend, c: '#E6EEF9' },
  { l: I.value.money.m3, v: I.value.money.balance, c: balC.value },
])
const overLabel = computed(() => (I.value.id === 'hosp' ? '缺口高于均值' : '超预算'))
const anyOver = computed(() => months.value.some(m => m.over))
</script>

<template>
  <div class="absolute top-[200px] left-[1252px] flex h-[564px] w-[640px] flex-col gap-4">
    <!-- 钱:左侧三项读数,右侧近 12 月 2.5D 柱(常规月低饱和蓝,超支月对比色,当月高亮) -->
    <div :class="panel">
      <div class="flex items-center justify-between">
        <PanelTitle :title="I.money.title" bar="rgb(var(--ck-acc))" />
        <div class="flex items-center gap-3.5 text-sm text-[#9FB2D1]">
          <span class="flex items-center gap-1.5"><span class="h-2 w-3 rounded-[2px] bg-[rgb(var(--ck-acc)/.55)]" />{{ I.money.legendBar }}</span>
          <span v-if="anyOver" class="flex items-center gap-1.5"><span class="h-2 w-3 rounded-[2px] bg-[rgba(255,107,94,.75)]" />{{ overLabel }}</span>
          <span class="flex items-center gap-1.5"><span class="w-3.5 border-t-2 border-dashed border-[#F5B74E]" />{{ I.money.legendLine }}</span>
          <span class="text-[#6F84A6]">{{ I.money.unit }}</span>
        </div>
      </div>
      <div class="mt-2 flex min-h-0 flex-1 gap-5">
        <div class="flex w-[224px] shrink-0 flex-col justify-around border-r border-[rgb(var(--ck-line)/.12)] pr-4">
          <div v-for="r in moneyRows" :key="r.l" class="flex items-baseline justify-between gap-2">
            <span class="min-w-0 truncate text-sm text-[#9FB2D1]" :title="r.l">{{ r.l }}</span>
            <AnimatedNumber :value="r.v" :replay="s.pulse" class="yb-num shrink-0 text-[22px] leading-tight font-semibold whitespace-nowrap" :style="{ color: r.c }" />
          </div>
        </div>
        <div class="relative flex min-w-0 flex-1 items-end gap-2 pt-5">
          <div v-for="(m, i) in months" :key="i" class="relative flex h-full flex-1 flex-col items-center justify-end gap-1">
            <!-- 跨年分隔 -->
            <template v-if="m.yr">
              <span class="absolute -top-5 bottom-[18px] -left-[5px] border-l border-dashed border-[rgb(var(--ck-accl)/.3)]" />
              <span class="yb-num absolute -top-5 left-0.5 text-sm leading-none text-[#6F84A6]">{{ m.yr }}</span>
            </template>
            <div
              class="relative w-full transition-[height] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]"
              :style="{ height: s.intro ? m.h : '0%', transitionDelay: i * 45 + 'ms' }"
            >
              <span
                v-if="m.cur"
                class="yb-num absolute -top-6 left-1/2 -translate-x-1/2 text-sm font-semibold whitespace-nowrap"
                :style="{ color: m.vc }"
              >{{ m.v }}</span>
              <!-- 2.5D 立方柱:正面 + 右侧面 + 顶面,柱高仍与数据一一对应 -->
              <div class="absolute top-[4px] right-[5px] bottom-0 left-0" :style="{ background: m.bg }" />
              <div class="cube-side absolute inset-y-0 right-0 w-[5px]" :style="{ background: m.bg }" />
              <div class="cube-top absolute inset-x-0 top-0 h-[4px]" :style="{ background: m.bg }" />
              <div v-if="m.cur" class="absolute top-[4px] right-[5px] bottom-0 left-0" :style="{ boxShadow: `0 0 14px color-mix(in srgb,${m.vc} 40%,transparent)` }" />
              <div class="absolute -right-[2px] -left-[2px] z-[1] border-t-2 border-dashed border-[rgba(245,183,78,.8)]" :style="{ bottom: m.bOff }" />
            </div>
            <span :class="['text-sm leading-none', m.cur ? 'font-semibold text-[#C9D6EA]' : 'text-[#6F84A6]']">{{ m.l }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 效:每项一行;正常时只靠颜色表达,偏高 / 偏低才出状态标签 -->
    <div :class="panel">
      <div class="flex items-center justify-between">
        <PanelTitle :title="I.effTitle" bar="#5B8FD9" />
        <span class="text-sm text-[#6F84A6]">目标带 0.95–1.05</span>
      </div>
      <div class="mt-1 flex flex-1 flex-col justify-around">
        <div v-for="b in bullets" :key="b.k" class="flex items-center gap-4">
          <span class="w-[132px] shrink-0 truncate text-[15px] text-[#C9D6EA]">{{ b.k }}</span>
          <div class="relative h-2.5 flex-1 rounded-[3px] bg-[rgba(255,255,255,.06)]">
            <div class="absolute inset-y-0 left-[41.7%] w-[16.6%] bg-[rgba(63,209,160,.22)]" />
            <div class="absolute -top-[3px] -bottom-[3px] left-1/2 border-l border-[rgba(230,238,249,.5)]" />
            <div
              class="absolute -top-[5px] -ml-0.5 h-[20px] w-[5px] rounded-[2px] transition-[left] duration-[1100ms] ease-[cubic-bezier(.2,.8,.2,1)]"
              :style="{ left: s.intro ? b.p : '50%', background: b.c, boxShadow: `0 0 10px ${b.c}` }"
            />
          </div>
          <span class="w-[48px] shrink-0">
            <span v-if="b.st !== '正常'" class="block rounded px-1.5 py-px text-center text-sm" :style="{ color: b.c, background: `color-mix(in srgb,${b.c} 13%,transparent)` }">{{ b.st }}</span>
          </span>
          <AnimatedNumber :value="b.v" :replay="s.pulse" class="yb-num w-[52px] shrink-0 text-right text-[22px] leading-tight font-semibold" :style="{ color: b.c }" />
        </div>
      </div>
    </div>

    <!-- 错:四项读数 + 较上期变化(按好坏着色) -->
    <div :class="panel">
      <PanelTitle :title="I.errTitle" bar="#FF6B5E" />
      <div class="mt-2.5 grid flex-1 grid-cols-4 gap-2.5">
        <div
          v-for="e in errs"
          :key="e.label"
          class="relative flex flex-col justify-center overflow-hidden rounded-md border border-[rgb(var(--ck-line)/.1)] bg-[rgba(255,255,255,.03)] px-3"
        >
          <span class="absolute inset-x-0 top-0 h-[2px]" :style="{ background: e.c }" />
          <div class="truncate text-sm text-[#9FB2D1]">{{ e.label }}</div>
          <div class="whitespace-nowrap">
            <AnimatedNumber :value="e.value" :replay="s.pulse" class="yb-num text-[28px] leading-tight font-semibold" :style="{ color: e.c }" />
            <span class="text-sm text-[#6F84A6]"> {{ e.unit }}</span>
          </div>
          <div v-if="e.d" class="yb-num text-sm whitespace-nowrap" :style="{ color: e.dc }" :title="e.st ? `较上期 ${e.d} · ${e.st}` : `较上期 ${e.d}`">
            <span class="text-[#6F84A6]">较上期 </span>{{ e.d }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
