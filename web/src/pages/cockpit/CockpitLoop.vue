<script setup lang="ts">
import AnimatedNumber from './AnimatedNumber.vue'
import { useCockpit } from './store'

const { s, I, pipe } = useCockpit()
</script>

<template>
  <div
    class="absolute top-[916px] right-7 left-7 flex h-[140px] items-center gap-[18px] rounded-[10px] border border-[rgba(90,150,255,.16)] bg-[rgba(10,22,46,.66)] px-[26px] py-4"
  >
    <div class="w-[170px] shrink-0">
      <div class="text-xl font-semibold">{{ I.loopTitle }}</div>
      <div class="mt-1 text-sm text-[#6F84A6]">分析监测区 → 发布区</div>
    </div>
    <div v-for="p in pipe" :key="p.n" class="flex min-w-0 flex-1 items-center gap-3.5">
      <div class="flex-1 rounded-lg border px-[18px] py-3.5" :style="{ background: p.bg, borderColor: p.bd }">
        <div class="flex items-baseline justify-between">
          <span class="text-base font-medium text-[#C9D6EA]">{{ p.n }}</span>
          <AnimatedNumber :value="p.v" :replay="s.pulse" class="yb-num text-[34px] leading-none font-semibold" :style="{ color: p.c }" />
        </div>
        <div class="mt-2.5 mb-1.5 h-1 rounded-[2px] bg-[rgba(255,255,255,.06)]">
          <div class="h-1 rounded-[2px] transition-[width] duration-[1100ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? p.pct : '0%', background: p.c, boxShadow: `0 0 8px ${p.c}88` }" />
        </div>
        <div class="truncate text-[13px] text-[#6F84A6]">{{ p.sub }}</div>
      </div>
      <span v-if="p.arrow" class="text-2xl text-[#2E4A75]">›</span>
    </div>
  </div>
</template>
