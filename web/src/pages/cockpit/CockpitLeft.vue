<script setup lang="ts">
import { computed } from 'vue'
import PanelTitle from './PanelTitle.vue'
import { K, useCockpit } from './store'
import { vPress } from '@/lib/a11y'

const { s, I, months, torn } = useCockpit()
const balC = computed(() => (I.value.money.balance.startsWith('−') ? K.red : K.green))
const panel = 'rounded-[10px] border border-[rgba(90,150,255,.16)] bg-[rgba(10,22,46,.66)] px-[22px] py-[18px]'
</script>

<template>
  <div class="absolute top-[216px] left-7 flex h-[684px] w-[448px] flex-col gap-4">
    <!-- 钱 -->
    <div :class="[panel, 'flex flex-1 flex-col']">
      <div class="flex items-center justify-between">
        <PanelTitle :title="I.money.title" bar="#3AA0FF" />
        <span class="text-sm text-[#6F84A6]">{{ I.money.unit }}</span>
      </div>
      <div class="mt-3 mb-1 flex gap-7">
        <div>
          <div class="text-sm text-[#9FB2D1]">{{ I.money.m1 }}</div>
          <div class="yb-num text-[30px] font-semibold text-[#7FC3FF]">{{ I.money.budget }}</div>
        </div>
        <div>
          <div class="text-sm text-[#9FB2D1]">{{ I.money.m2 }}</div>
          <div class="yb-num text-[30px] font-semibold">{{ I.money.spend }}</div>
        </div>
        <div>
          <div class="text-sm text-[#9FB2D1]">{{ I.money.m3 }}</div>
          <div class="yb-num text-[30px] font-semibold" :style="{ color: balC }">{{ I.money.balance }}</div>
        </div>
      </div>
      <div class="relative flex flex-1 items-end gap-2.5 pt-3">
        <div v-for="(m, i) in months" :key="i" class="flex h-full flex-1 flex-col items-center justify-end gap-1.5">
          <div class="relative w-full" :style="{ height: m.h }">
            <div class="absolute -right-[3px] -left-[3px] border-t-2 border-dashed border-[rgba(245,183,78,.7)]" :style="{ bottom: m.bOff }" />
            <div class="absolute inset-0 rounded-t-[3px]" :style="{ background: m.bg }" />
          </div>
          <span class="text-[13px] text-[#6F84A6]">{{ m.l }}</span>
        </div>
      </div>
      <div class="mt-2 flex gap-[18px] text-[13px] text-[#9FB2D1]">
        <span class="flex items-center gap-1.5"><span class="h-2 w-3 rounded-[2px] bg-[#3AA0FF]" />{{ I.money.legendBar }}</span>
        <span class="flex items-center gap-1.5"><span class="w-3.5 border-t-2 border-dashed border-[#F5B74E]" />{{ I.money.legendLine }}</span>
      </div>
    </div>

    <!-- tornado -->
    <div :class="[panel, 'flex h-[316px] flex-col']">
      <div class="mb-3 flex items-center justify-between">
        <PanelTitle :title="I.tornTitle" bar="#FF6B5E" />
        <span class="text-sm text-[#6F84A6]">差额总额</span>
      </div>
      <div v-press
        v-for="(t, j) in torn"
        :key="j"
        class="relative grid min-h-[30px] flex-1 cursor-pointer grid-cols-2 items-center"
        @click="t.k && (s.sel = t.k)"
      >
        <div class="flex items-center justify-end gap-2 pr-0.5">
          <span class="text-sm text-[#9FE3C7]">{{ t.lt }}</span>
          <span class="h-[18px] rounded-l-[2px] bg-[#3FD1A0] opacity-85" :style="{ width: t.lw }" />
        </div>
        <div class="flex items-center gap-2 border-l border-[rgba(230,238,249,.3)] pl-0.5">
          <span class="h-[18px] rounded-r-[2px] bg-[#FF6B5E] opacity-90" :style="{ width: t.rw }" />
          <span class="text-sm text-[#FFB2AA]">{{ t.rt }}</span>
        </div>
      </div>
      <div class="mt-1.5 flex justify-between text-[13px] text-[#6F84A6]"><span>← 结余</span><span>逆差 →</span></div>
    </div>
  </div>
</template>
