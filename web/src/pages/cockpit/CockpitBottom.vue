<script setup lang="ts">
import AlertsPanel from './AlertsPanel.vue'
import PanelTitle from './PanelTitle.vue'
import { useCockpit } from './store'
import { vPress } from '@/lib/a11y'

/* 下排左、中:病组差异 · 实时提醒(见 AlertsPanel);闭环见 CockpitLoop,与右栏对齐 */
const { s, I, torn } = useCockpit()
const panel = 'absolute top-[780px] flex h-[276px] flex-col rounded-[10px] border border-[rgba(90,150,255,.16)] bg-[rgba(10,22,46,.66)] px-[22px] py-3.5'

</script>

<template>
  <!-- 病组差异:左结余、右逆差;编码与金额分列,金额右对齐便于比较 -->
  <div :class="panel" class="left-7 w-[560px]">
    <div class="mb-2 flex items-center justify-between">
      <PanelTitle :title="I.tornTitle" bar="#FF6B5E" />
      <span class="text-sm text-[#6F84A6]">差额总额</span>
    </div>
    <div v-press
      v-for="(t, j) in torn"
      :key="j"
      class="grid min-h-0 flex-1 cursor-pointer grid-cols-[52px_64px_1fr_1fr_52px_64px] items-center gap-x-2 rounded-[4px] hover:bg-[rgba(90,150,255,.06)]"
      @click="t.k && (s.sel = t.k)"
    >
      <span class="yb-num text-sm text-[#9FE3C7]">{{ t.lk }}</span>
      <span class="yb-num text-right text-sm font-semibold text-[#9FE3C7]">{{ t.la }}</span>
      <div class="flex h-full items-center justify-end border-r border-[rgba(230,238,249,.3)]">
        <span class="h-4 rounded-l-[2px] bg-[#3FD1A0] opacity-85 transition-[width] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? t.lw : '0', transitionDelay: j * 70 + 'ms' }" />
      </div>
      <div class="flex h-full items-center">
        <span class="h-4 rounded-r-[2px] bg-[#FF6B5E] opacity-90 transition-[width] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? t.rw : '0', transitionDelay: j * 70 + 'ms' }" />
      </div>
      <span class="yb-num text-sm text-[#FFB2AA]">{{ t.rk }}</span>
      <span class="yb-num text-right text-sm font-semibold text-[#FFB2AA]">{{ t.ra }}</span>
    </div>
    <div class="mt-1 grid grid-cols-[52px_64px_1fr_1fr_52px_64px] gap-x-2 text-sm text-[#6F84A6]">
      <span class="col-span-3 text-right">← 结余</span><span class="col-span-3">逆差 →</span>
    </div>
  </div>

  <AlertsPanel class="left-[604px] w-[632px]" />
</template>
