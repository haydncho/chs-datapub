<script setup lang="ts">
import type { Tone } from '@/api/types'
import StrokeIcon from '@/components/ui/stroke-icon/StrokeIcon.vue'
import { iconPaths } from '@/lib/icons'
import { toneSoft, toneVar } from '@/lib/tone'

/** KPI 卡（睿衡）：135° 浅色渐变底、大号取值、右上图标。 */
withDefaults(defineProps<{ label: string; value: string; unit?: string; desc?: string; tone?: Tone; icon?: string; small?: boolean }>(), {
  tone: 'primary',
  icon: 'pct',
})
</script>

<template>
  <div
    class="relative overflow-hidden rounded-[10px] border border-line px-[18px] pt-[14px] pb-[13px]"
    :style="{ background: `linear-gradient(135deg, ${toneSoft[tone]} 0%, var(--c-surface) 66%)` }"
  >
    <div class="text-[12px] leading-[1.6] text-ink-muted">{{ label }}</div>
    <div class="mt-1.5 flex items-baseline gap-[6px]">
      <span class="leading-[1.1] font-bold" :class="small ? 'text-[18px]' : 'text-[22px]'" :style="{ color: tone === 'muted' ? 'var(--c-text)' : toneVar[tone] }">{{ value }}</span>
      <span v-if="unit" class="text-[12px] text-ink-faint">{{ unit }}</span>
    </div>
    <div v-if="desc" class="mt-1.5 text-[12px] leading-[1.6] text-ink-muted"><slot name="desc">{{ desc }}</slot></div>
    <span class="absolute top-3 right-3 flex size-7 items-center justify-center rounded-lg" :style="{ background: toneSoft[tone], color: toneVar[tone] }">
      <StrokeIcon :d="iconPaths[icon]" :size="15" />
    </span>
  </div>
</template>
