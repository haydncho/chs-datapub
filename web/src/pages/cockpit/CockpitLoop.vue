<script setup lang="ts">
import AnimatedNumber from './AnimatedNumber.vue'
import PanelTitle from './PanelTitle.vue'
import { useCockpit } from './store'

/* 下排右:五环节闭环,竖向步骤条(与右栏钱 / 效 / 错同宽对齐);当前环节蓝框高亮,进度最低的环节标「卡点」,有超期事项标「超期」 */
const { s, I, pipe } = useCockpit()
</script>

<template>
  <div
    class="absolute top-[780px] left-[1252px] flex h-[276px] w-[640px] flex-col rounded-[10px] border border-[rgba(90,150,255,.16)] bg-[rgba(10,22,46,.66)] px-[22px] py-3.5"
  >
    <div class="mb-1.5 flex items-center justify-between">
      <PanelTitle :title="I.loopTitle" bar="#3FD1A0" />
      <span class="text-sm text-[#6F84A6]">分析监测区 → 发布区</span>
    </div>
    <div class="flex flex-1 flex-col">
      <div
        v-for="(p, i) in pipe"
        :key="p.n"
        class="relative flex min-h-0 flex-1 items-center gap-3.5 rounded-md border px-2.5"
        :style="{ background: p.act ? p.bg : 'transparent', borderColor: p.act ? p.bd : 'transparent' }"
      >
        <!-- 步骤节点与连线 -->
        <span class="relative flex w-3 shrink-0 justify-center self-stretch">
          <span v-if="i > 0" class="absolute top-0 bottom-1/2 w-px bg-[rgba(90,150,255,.28)]" />
          <span v-if="p.arrow" class="absolute top-1/2 bottom-0 w-px bg-[rgba(90,150,255,.28)]" />
          <span class="relative z-[1] size-2.5 self-center rounded-full" :style="{ background: p.c, boxShadow: `0 0 8px ${p.c}` }" />
        </span>
        <span class="w-[78px] shrink-0 text-[15px] font-medium text-[#C9D6EA]">{{ p.n }}</span>
        <span class="flex w-[44px] shrink-0 flex-col gap-0.5">
          <span v-if="p.lag" class="rounded-[3px] bg-[#F5B74E] text-center text-sm leading-[20px] font-semibold text-[#04101F]" title="进度最低的环节">卡点</span>
          <span v-else-if="p.overdue" class="rounded-[3px] border border-[#FF6B5E] text-center text-sm leading-[18px] text-[#FF8A7E]" title="有超期事项">超期</span>
        </span>
        <div class="h-1.5 flex-1 rounded-[2px] bg-[rgba(255,255,255,.06)]">
          <div class="h-1.5 rounded-[2px] transition-[width] duration-[1100ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? p.pct : '0%', background: p.c, boxShadow: `0 0 8px ${p.c}88`, transitionDelay: i * 80 + 'ms' }" />
        </div>
        <AnimatedNumber :value="p.v" :replay="s.pulse" class="yb-num w-[60px] shrink-0 text-right text-[22px] leading-none font-semibold" :style="{ color: p.c }" />
        <span class="w-[176px] shrink-0 truncate text-sm text-[#9FB2D1]" :title="p.sub">{{ p.sub }}</span>
      </div>
    </div>
  </div>
</template>
