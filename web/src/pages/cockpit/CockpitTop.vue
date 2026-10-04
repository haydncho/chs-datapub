<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useCockpit, type Period } from './store'

const { s, I, ribbon, clock, data } = useCockpit()
const PERIODS: Period[] = ['月', '季', '年']
</script>

<template>
  <!-- title bar -->
  <div class="absolute top-0 right-7 left-7 flex h-[72px] items-center">
    <div class="flex w-[600px] items-center gap-3.5">
      <div class="flex size-9 items-center justify-center rounded-lg bg-brand text-lg font-bold">医</div>
      <div class="leading-[1.3]">
        <div class="text-[17px] font-semibold">{{ I.org }}</div>
        <div class="text-[13px] text-[#6F84A6]">医保数据工作组 · {{ I.zone }}</div>
      </div>
    </div>
    <div class="flex-1 text-center">
      <div class="text-[34px] font-semibold tracking-[8px] text-[#F4F8FF]">{{ I.title }}</div>
    </div>
    <div class="flex w-[600px] items-center justify-end gap-4">
      <div class="flex overflow-hidden rounded-md border border-[rgba(90,150,255,.3)]">
        <button type="button"
          v-for="p in PERIODS"
          :key="p"
          :class="cn(
            'cursor-pointer px-4 py-1 text-[15px] font-semibold',
            p === s.period ? 'bg-[#3AA0FF] text-[#04101F]' : 'text-[#9FB2D1]',
          )"
          @click="s.period = p"
        >{{ p }}</button>
      </div>
      <div class="text-right leading-[1.2]">
        <div class="yb-num text-[28px] font-medium tracking-[1px]">{{ clock.time }}</div>
        <div class="text-[13px] text-[#6F84A6]">{{ clock.date }} · 数据截至 {{ data.dataAsOf }}</div>
      </div>
    </div>
  </div>

  <!-- KPI ribbon -->
  <div
    class="absolute top-[84px] right-7 left-7 grid h-[116px] grid-cols-6 rounded-[10px] border border-[rgba(90,150,255,.18)] bg-[linear-gradient(180deg,rgba(18,40,82,.55),rgba(10,22,46,.35))]"
  >
    <div
      v-for="k in ribbon"
      :key="k.k"
      :class="cn(
        'flex flex-col justify-between border-l px-6 py-4',
        k.first ? 'border-transparent' : 'border-[rgba(90,150,255,.14)]',
      )"
    >
      <div class="flex items-center justify-between">
        <span class="text-base text-[#9FB2D1]">{{ k.k }}</span>
        <span class="yb-num text-[15px] font-semibold" :style="{ color: k.dc }">{{ k.d }}</span>
      </div>
      <div class="flex items-end justify-between gap-3">
        <div class="flex items-baseline gap-1">
          <span class="yb-num text-[46px] leading-none font-semibold text-[#F4F8FF]">{{ k.v }}</span>
          <span class="text-[13px] text-[#9FB2D1]">{{ k.u }}</span>
        </div>
        <div class="flex h-[34px] items-end gap-[3px]">
          <span v-for="(b, j) in k.sp" :key="j" class="w-[5px] rounded-[1px]" :style="{ height: b.h, background: b.c }" />
        </div>
      </div>
    </div>
  </div>
</template>
