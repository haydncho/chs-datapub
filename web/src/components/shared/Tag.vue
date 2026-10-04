<script setup lang="ts">
import type { Tone } from '@/api/types'
import { toneTag } from '@/lib/tone'

/** 状态徽标：浅底 + 深字 + 细描边，底 / 字成对取自语义令牌。 */
withDefaults(defineProps<{ tone?: Tone }>(), { tone: 'muted' })
</script>

<template>
  <span class="inline-flex items-center rounded-full border px-2 py-px text-[11px] leading-[1.6] whitespace-nowrap" :class="toneTag[tone]"><slot /></span>
</template>
