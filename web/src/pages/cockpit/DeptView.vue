<script setup lang="ts">
import { useCockpit } from './store'
import { vPress } from '@/lib/a11y'

const { s, depts } = useCockpit()
</script>

<template>
  <div class="grid h-full grid-cols-4 grid-rows-[repeat(3,minmax(0,1fr))] gap-3">
    <div v-press
      v-for="t in depts"
      :key="t.n"
      class="flex min-h-0 cursor-pointer flex-col justify-between rounded-lg border px-4 py-3"
      :style="t.style"
      @click="s.sel = t.n"
    >
      <div class="flex items-baseline justify-between gap-2">
        <span class="text-[17px] font-semibold whitespace-nowrap">{{ t.n }}</span>
        <span class="yb-num text-sm whitespace-nowrap text-[#9FB2D1]">{{ t.c }} 例</span>
      </div>
      <div class="whitespace-nowrap">
        <span class="yb-num text-[30px] font-semibold" :style="{ color: t.fg }">{{ t.v }}</span>
        <span class="text-[13px] text-[#9FB2D1]"> 元/例</span>
      </div>
      <div class="flex justify-between text-[13px] whitespace-nowrap text-[#9FB2D1]">
        <span>CMI {{ t.cmi }}</span>
        <span :style="{ color: t.fg }">{{ t.tot }}</span>
      </div>
    </div>
  </div>
</template>
