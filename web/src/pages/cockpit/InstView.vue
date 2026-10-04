<script setup lang="ts">
import { useCockpit } from './store'
import { vPress } from '@/lib/a11y'

const { s, instCols } = useCockpit()
</script>

<template>
  <div class="grid h-full grid-cols-4 gap-3.5">
    <div v-for="col in instCols" :key="col.d" class="flex min-w-0 flex-col gap-2">
      <div class="flex items-baseline justify-between border-b border-[rgba(90,150,255,.18)] pb-1.5">
        <span class="text-[17px] font-semibold" :style="{ color: col.hc }">{{ col.d }}</span>
        <span class="yb-num text-[15px]" :style="{ color: col.ac }">{{ col.avg }}</span>
      </div>
      <div v-if="col.open" class="grid grid-cols-2 gap-1.5">
        <div v-press
          v-for="t in col.tiles"
          :key="t.n"
          :title="t.tip"
          class="flex h-[54px] cursor-pointer flex-col justify-between overflow-hidden rounded-md border px-2 py-1.5"
          :style="{ background: t.bg, borderColor: t.bd }"
          @click="s.sel = t.n"
        >
          <span class="truncate text-xs text-[#C9D6EA]">{{ t.n }}</span>
          <span class="yb-num text-[17px] font-semibold" :style="{ color: t.fg }">{{ t.v }}</span>
        </div>
      </div>
      <div
        v-else
        class="flex flex-1 flex-col items-center justify-center gap-1.5 rounded-lg border border-dashed border-[rgba(143,163,196,.3)] text-sm text-[#6F84A6]"
      >
        <span class="yb-num text-[28px] text-[#C9D6EA]">{{ col.avg }}</span>
        <span>{{ col.n }} 家 · 仅汇总</span>
        <span class="text-[13px]">机构明细不渲染</span>
      </div>
    </div>
  </div>
</template>
