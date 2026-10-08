<script setup lang="ts">
import { Check } from '@lucide/vue'
import { THEME_COLORS, THEME_SOFT, type Appearance } from '@/app/appearance'
import type { A15Palette, A15Preset } from '@/mock/A15'

/** 预设方案缩略卡:按方案自己的主题色 / 圆角 / 卡片样式 / 密度 / 大屏配色画一张小图。 */
defineProps<{ presets: A15Preset[]; palettes: A15Palette[]; draft: Appearance; isActive: (p: A15Preset) => boolean }>()
defineEmits<{ apply: [p: A15Preset] }>()

const RAD = ['2px', '5px', '8px']
const GAP = ['2px', '4px', '6px']
</script>

<template>
  <div class="grid grid-cols-[repeat(auto-fit,minmax(96px,1fr))] gap-2 max-lg:grid-cols-3" role="group" aria-label="预设方案">
    <button
      v-for="p in presets"
      :key="p.id"
      type="button"
      class="flex cursor-pointer flex-col gap-2 rounded-[10px] border-[1.5px] bg-white p-2 text-left hover:bg-surface-1"
      :class="isActive(p) ? 'border-brand' : 'border-line-1'"
      :aria-pressed="isActive(p)"
      :aria-label="`应用预设方案 ${p.name}`"
      @click="$emit('apply', p)"
    >
      <div class="flex h-[78px] w-full flex-col overflow-hidden rounded-md border border-line-2 bg-[#F4F6F9]" aria-hidden="true">
        <div class="flex h-3.5 shrink-0 items-center gap-1 bg-white px-1.5">
          <span class="size-1.5 rounded-[2px]" :style="{ background: THEME_COLORS[p.patch.c] }" />
          <span class="h-[3px] w-5 rounded-full bg-line-4" />
          <span class="h-[3px] w-3 rounded-full" :style="{ background: THEME_COLORS[p.patch.c], opacity: 0.55 }" />
          <span class="h-[3px] w-3 rounded-full bg-line-4" />
        </div>
        <div class="flex min-h-0 flex-1 flex-col p-1" :style="{ gap: GAP[p.patch.dens] }">
          <div class="flex flex-1" :style="{ gap: GAP[p.patch.dens] }">
            <span
              v-for="k in 2"
              :key="k"
              class="flex-1 border"
              :style="{
                borderRadius: RAD[p.patch.rad],
                borderColor: p.patch.card === 0 ? '#DCE1E9' : p.patch.card === 1 ? '#DCE6F8' : '#EEF1F5',
                background: p.patch.card === 1 ? `linear-gradient(135deg, ${THEME_SOFT[p.patch.c]}, #fff 70%)` : '#fff',
                boxShadow: p.patch.card === 2 ? '0 2px 5px rgba(15,23,42,.12)' : 'none',
              }"
            />
          </div>
          <div class="h-3 rounded-[2px]" :style="{ background: palettes[p.patch.scr]?.bg }">
            <span class="mt-[4px] ml-2 block size-1 rounded-full" :style="{ background: palettes[p.patch.scr]?.accent }" />
          </div>
        </div>
      </div>
      <div class="flex items-center gap-1">
        <span class="min-w-0 flex-1 truncate text-xs font-medium">{{ p.name }}</span>
        <span v-if="isActive(p)" class="flex shrink-0 items-center gap-0.5 text-[11px] font-semibold text-brand"><Check class="size-3" :stroke-width="3" />当前</span>
      </div>
      <span class="-mt-1 truncate text-[11px] text-ink-4">{{ p.desc }}</span>
    </button>
  </div>
</template>
