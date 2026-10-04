<script setup lang="ts">
import { computed } from 'vue'
import PanelTitle from './PanelTitle.vue'
import { TONE_C, useCockpit } from './store'

const { I, bullets, alertsAll } = useCockpit()
const alerts = computed(() => alertsAll.value.slice(0, 4))
const panel = 'rounded-[10px] border border-[rgba(90,150,255,.16)] bg-[rgba(10,22,46,.66)] px-[22px] py-[18px]'
</script>

<template>
  <div class="absolute top-[216px] left-[1444px] flex h-[684px] w-[448px] flex-col gap-4">
    <!-- 效 -->
    <div :class="panel">
      <div class="mb-3.5 flex items-center justify-between">
        <PanelTitle :title="I.effTitle" bar="#5B8FD9" />
        <span class="text-sm text-[#6F84A6]">目标带 0.95–1.05</span>
      </div>
      <div v-for="b in bullets" :key="b.k" class="mb-3.5">
        <div class="flex items-baseline justify-between">
          <span class="text-base text-[#C9D6EA]">{{ b.k }}</span>
          <span class="yb-num text-2xl font-semibold" :style="{ color: b.c }">{{ b.v }}</span>
        </div>
        <div class="relative mt-1.5 h-3 rounded-[3px] bg-[rgba(255,255,255,.06)]">
          <div class="absolute inset-y-0 left-[41.7%] w-[16.6%] bg-[rgba(63,209,160,.22)]" />
          <div class="absolute -top-[3px] -bottom-[3px] left-1/2 border-l border-[rgba(230,238,249,.5)]" />
          <div
            class="absolute -top-[5px] -ml-0.5 h-[22px] w-[5px] rounded-[2px]"
            :style="{ left: b.p, background: b.c, boxShadow: `0 0 10px ${b.c}` }"
          />
        </div>
      </div>
    </div>

    <!-- 错 -->
    <div :class="panel">
      <div class="mb-3"><PanelTitle :title="I.errTitle" bar="#FF6B5E" /></div>
      <div class="grid grid-cols-2 gap-2.5">
        <div v-for="e in I.errs" :key="e.label" class="rounded-md border border-[rgba(90,150,255,.1)] bg-[rgba(255,255,255,.03)] px-3 py-2.5">
          <div class="text-sm text-[#9FB2D1]">{{ e.label }}</div>
          <div>
            <span class="yb-num text-[28px] font-semibold" :style="{ color: TONE_C[e.tone] }">{{ e.value }}</span>
            <span class="text-xs text-[#6F84A6]"> {{ e.unit }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- alerts -->
    <div :class="[panel, 'flex-1 overflow-hidden']">
      <div class="mb-2 flex items-center justify-between">
        <PanelTitle :title="I.alertTitle" bar="#F5B74E" />
        <span class="yb-num text-[15px] text-[#F5B74E]">{{ I.alerts.length }} 条</span>
      </div>
      <div v-for="(a, i) in alerts" :key="i" class="flex items-start gap-2.5 border-t border-[rgba(90,150,255,.1)] py-2">
        <span class="mt-0.5 rounded-[3px] border px-[7px] py-px text-xs whitespace-nowrap" :style="{ borderColor: a.c, color: a.c }">{{ a.ty }}</span>
        <span class="flex-1 text-[15px] text-[#DDE6F3]">{{ a.txt }}</span>
        <span class="yb-num text-sm text-[#6F84A6]">{{ a.t }}</span>
      </div>
    </div>
  </div>
</template>
