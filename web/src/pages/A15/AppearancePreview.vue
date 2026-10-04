<script setup lang="ts">
import { computed } from 'vue'
import { THEME_COLORS, THEME_SOFT, type Appearance } from '@/app/appearance'
import type { A15Palette, A15PreviewRow } from '@/mock/A15'

/** Right-hand live preview of the DRAFT appearance (nav bar, KPI cards, table, buttons, mini cockpit). */
const props = defineProps<{ draft: Appearance; palettes: A15Palette[]; rows: A15PreviewRow[] }>()

const ap = computed(() => {
  const d = props.draft
  const c = THEME_COLORS[d.c] ?? THEME_COLORS[0]
  const cs = THEME_SOFT[d.c] ?? THEME_SOFT[0]
  const scr = props.palettes[d.scr] ?? props.palettes[0]!
  return {
    c,
    cs,
    fs: ['12px', '13px', '14px'][d.font],
    pad: ['10px', '14px', '18px'][d.dens],
    gap: ['8px', '12px', '16px'][d.dens],
    row: ['6px', '9px', '12px'][d.dens],
    r: ['4px', '12px', '18px'][d.rad],
    br: ['4px', '8px', '999px'][d.rad],
    cb: d.card === 1 ? '#DCE6F8' : '#E5E9F0',
    cardBg: d.card === 1 ? `linear-gradient(135deg,${cs} 0%,#fff 64%)` : '#fff',
    sh: d.card === 2 ? '0 4px 14px rgba(15,23,42,.08)' : 'none',
    sBg: scr?.bg,
    sP: scr?.panel,
    sA: scr?.accent,
  }
})
const card = computed(() => ({ borderRadius: ap.value.r, borderColor: ap.value.cb, background: ap.value.cardBg, padding: ap.value.pad, boxShadow: ap.value.sh }))
</script>

<template>
  <div class="sticky top-(--sticky-panel) flex flex-col gap-3">
    <div class="text-xs font-semibold text-ink-4">实时预览</div>
    <div class="overflow-hidden rounded-[12px] border border-line-1 bg-background" :style="{ fontSize: ap.fs }">
      <div class="flex h-11 items-center gap-2 border-b border-line-1 bg-white px-3.5">
        <span class="flex size-6 items-center justify-center rounded-[7px] text-xs font-bold text-white" :style="{ background: ap.c }">医</span>
        <span class="font-semibold whitespace-nowrap">{{ draft.name }}</span>
        <div class="flex-1" />
        <span class="rounded-md px-2.5 py-1 text-xs font-semibold whitespace-nowrap" :style="{ background: ap.cs, color: ap.c }">02 配置</span>
      </div>
      <div class="flex flex-col" :style="{ padding: ap.pad, gap: ap.gap }">
        <div class="grid grid-cols-2" :style="{ gap: ap.gap }">
          <div class="border" :style="card">
            <div class="text-[11px] text-ink-4">例均基金差额</div>
            <div class="yb-num text-2xl font-semibold text-bad">+486</div>
            <div class="relative mt-1.5 h-1.5 rounded-[3px] bg-line-2">
              <span class="absolute top-[-3px] left-[62%] h-3 w-1 rounded-[2px]" :style="{ background: ap.c }" />
            </div>
          </div>
          <div class="border" :style="card">
            <div class="text-[11px] text-ink-4">CMI</div>
            <div class="yb-num text-2xl font-semibold">1.12</div>
            <div class="text-[11px]" :style="{ color: ap.c }">同级 P68</div>
          </div>
        </div>
        <div class="overflow-hidden border bg-white" :style="{ borderRadius: ap.r, borderColor: ap.cb, boxShadow: ap.sh }">
          <div
            v-for="r in rows"
            :key="r.name"
            class="flex justify-between border-b border-line-3 px-3"
            :style="{ paddingTop: ap.row, paddingBottom: ap.row }"
          >
            <span>{{ r.name }}</span>
            <span :class="['yb-num font-semibold', r.tone === 'bad' ? 'text-bad' : 'text-ok-ink']">{{ r.value }}</span>
          </div>
        </div>
        <div class="flex gap-2">
          <span class="flex h-8 items-center px-3.5 text-xs font-semibold whitespace-nowrap text-white" :style="{ borderRadius: ap.br, background: ap.c }">主按钮</span>
          <span class="flex h-8 items-center border border-line-1 bg-white px-3.5 text-xs whitespace-nowrap" :style="{ borderRadius: ap.br }">次按钮</span>
        </div>
      </div>
    </div>
    <div class="relative h-[150px] overflow-hidden rounded-[12px]" :style="{ background: ap.sBg }">
      <span class="absolute top-2.5 left-3.5 text-[13px] font-semibold tracking-[3px] text-[#F4F8FF]">医保数据全息图</span>
      <span class="absolute top-9 right-3.5 left-3.5 h-[22px] rounded" :style="{ background: ap.sP }" />
      <span class="absolute top-[66px] bottom-3.5 left-3.5 w-[30%] rounded" :style="{ background: ap.sP }" />
      <span class="absolute top-[66px] right-3.5 bottom-3.5 left-[36%] rounded" :style="{ background: ap.sP }" />
      <span class="absolute top-[84px] left-[56%] size-5 rounded-full" :style="{ background: ap.sA, boxShadow: `0 0 16px ${ap.sA}` }" />
      <span class="absolute top-[100px] left-[74%] size-3 rounded-full bg-[#3FD1A0]" />
      <span class="absolute top-[78px] left-[66%] size-[9px] rounded-full bg-[#FF6B5E]" />
    </div>
  </div>
</template>
