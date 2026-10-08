<script setup lang="ts">
import AlertsPanel from './AlertsPanel.vue'
import PanelTitle from './PanelTitle.vue'
import { useCockpit } from './store'
import { vPress } from '@/lib/a11y'

/* 下排左、中:病组差异 · 实时提醒(见 AlertsPanel);闭环见 CockpitLoop,与右栏对齐 */
const { s, I, torn, pickDrg } = useCockpit()
const panel = 'absolute top-[780px] flex h-[276px] flex-col rounded-[10px] border border-[rgb(var(--ck-line)/.16)] bg-[rgb(var(--ck-panel)/.66)] px-[22px] py-3.5'

</script>

<template>
  <!-- 病组差异:左结余、右逆差;编码与金额分列,金额右对齐便于比较 -->
  <div :class="panel" class="left-7 w-[560px]">
    <div class="mb-2 flex items-center justify-between">
      <PanelTitle :title="I.tornTitle" bar="#FF6B5E" />
      <span class="text-sm text-[#6F84A6]">差额总额</span>
    </div>
    <!-- 每行左右两半各自可点:左半选中结余病组,右半选中逆差病组 -->
    <div
      v-for="(t, j) in torn"
      :key="j"
      class="grid min-h-0 flex-1 grid-cols-[52px_64px_1fr_1fr_52px_64px] items-center gap-x-2"
    >
      <div v-press="!!t.lk"
        :class="['col-span-3 grid h-full grid-cols-[52px_64px_1fr] items-center gap-x-2 rounded-l-[4px]', t.lk && 'cursor-pointer hover:bg-[rgb(var(--ck-line)/.06)]', t.lon && 'bg-[rgb(var(--ck-line)/.1)]']"
        :aria-label="t.lk ? `选中病组 ${t.lk}` : undefined"
        data-testid="torn-left"
        @click="pickDrg(t.lk)"
      >
        <span class="yb-num text-sm text-[#9FE3C7]">{{ t.lk }}</span>
        <span class="yb-num text-right text-sm font-semibold text-[#9FE3C7]">{{ t.la }}</span>
        <div class="flex h-full items-center justify-end border-r border-[rgba(230,238,249,.3)]">
          <span class="h-4 rounded-l-[2px] bg-[#3FD1A0] opacity-85 transition-[width] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? t.lw : '0', transitionDelay: j * 70 + 'ms' }" />
        </div>
      </div>
      <div v-press="!!t.rk"
        :class="['col-span-3 grid h-full grid-cols-[1fr_52px_64px] items-center gap-x-2 rounded-r-[4px]', t.rk && 'cursor-pointer hover:bg-[rgb(var(--ck-line)/.06)]', t.ron && 'bg-[rgb(var(--ck-line)/.1)]']"
        :aria-label="t.rk ? `选中病组 ${t.rk}` : undefined"
        data-testid="torn-right"
        @click="pickDrg(t.rk)"
      >
        <div class="flex h-full items-center">
          <span class="h-4 rounded-r-[2px] bg-[#FF6B5E] opacity-90 transition-[width] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? t.rw : '0', transitionDelay: j * 70 + 'ms' }" />
        </div>
        <span class="yb-num text-sm text-[#FFB2AA]">{{ t.rk }}</span>
        <span class="yb-num text-right text-sm font-semibold text-[#FFB2AA]">{{ t.ra }}</span>
      </div>
    </div>
    <div class="mt-1 grid grid-cols-[52px_64px_1fr_1fr_52px_64px] gap-x-2 text-sm text-[#6F84A6]">
      <span class="col-span-3 text-right">← 结余</span><span class="col-span-3">逆差 →</span>
    </div>
  </div>

  <AlertsPanel class="left-[604px] w-[632px]" />
</template>
