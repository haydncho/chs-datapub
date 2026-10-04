<script setup lang="ts">
import PanelTitle from './PanelTitle.vue'
import PeerView from './PeerView.vue'
import PubMatrix from './PubMatrix.vue'
import { useCockpit } from './store'

const { I, alertsAll, pipe, clock } = useCockpit()
const panel = 'rounded-[10px] border border-[rgba(90,150,255,.16)] bg-[rgba(10,22,46,.66)] px-[26px] py-[22px]'
</script>

<template>
  <div class="absolute top-0 right-7 left-7 flex h-[72px] items-center justify-between">
    <span class="text-[17px] whitespace-nowrap text-[#9FB2D1]">副屏 · {{ I.org }}</span>
    <span class="text-[30px] font-semibold tracking-[6px] whitespace-nowrap">{{ I.screen2Title }}</span>
    <span class="yb-num text-[28px] font-medium">{{ clock.time }}</span>
  </div>
  <div class="absolute top-24 bottom-7 left-7 flex w-[880px] flex-col gap-4">
    <div :class="[panel, 'flex min-h-0 flex-1 flex-col']">
      <div class="mb-2.5 flex items-center gap-2.5">
        <PanelTitle :title="I.alertTitle" bar="#F5B74E" size="lg" />
        <div class="flex-1" />
        <span class="yb-num text-lg text-[#F5B74E]">{{ I.alerts.length }} 条</span>
      </div>
      <div v-for="(a, i) in alertsAll" :key="i" class="flex items-center gap-3.5 border-t border-[rgba(90,150,255,.1)] py-4">
        <span class="rounded border px-2.5 py-0.5 text-sm whitespace-nowrap" :style="{ borderColor: a.c, color: a.c }">{{ a.ty }}</span>
        <span class="flex-1 truncate text-xl text-[#DDE6F3]">{{ a.txt }}</span>
        <span class="yb-num text-[17px] text-[#6F84A6]">{{ a.t }}</span>
      </div>
    </div>
    <div :class="[panel, 'h-[300px]']">
      <div class="mb-[18px]"><PanelTitle :title="I.loopTitle" bar="#3AA0FF" size="lg" /></div>
      <div class="flex flex-col gap-4">
        <div v-for="p in pipe" :key="p.n" class="grid grid-cols-[140px_minmax(0,1fr)_110px] items-center gap-4">
          <span class="text-[17px] whitespace-nowrap text-[#C9D6EA]">{{ p.n }}</span>
          <div class="h-3 rounded-md bg-[rgba(255,255,255,.06)]">
            <div class="h-3 rounded-md" :style="{ width: p.pct, background: p.c }" />
          </div>
          <span class="yb-num text-right text-[22px] font-semibold" :style="{ color: p.c }">{{ p.v }}</span>
        </div>
      </div>
    </div>
  </div>
  <div :class="[panel, 'absolute top-24 right-7 bottom-7 left-[924px] flex flex-col']">
    <div class="mb-3.5"><PanelTitle :title="I.screen2Right" bar="#3FD1A0" size="lg" /></div>
    <PubMatrix v-if="I.id !== 'hosp'" screen2 />
    <PeerView v-else screen2 />
  </div>
</template>
