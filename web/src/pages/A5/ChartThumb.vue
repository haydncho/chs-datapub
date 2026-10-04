<script setup lang="ts">
import { computed } from 'vue'
import type { A5Thumb } from '@/mock/A5'

const props = defineProps<{ thumb: A5Thumb }>()

const ACCENT = { brand: 'var(--brand)', ink: 'var(--ink-1)', warn: 'var(--warn)', bad: 'var(--bad)' } as const
const accent = computed(() => ACCENT[props.thumb.accentTone ?? 'brand'])
</script>

<template>
  <div class="h-14 rounded-md border border-[#EEF2F8] bg-[linear-gradient(135deg,#F3F7FE,#FFFFFF)] px-2 py-1.5">
    <svg viewBox="0 0 100 40" class="block size-full">
      <path v-if="thumb.track" :d="thumb.track" fill="#E6EBF2" />
      <path v-if="thumb.glow" :d="thumb.glow" fill="var(--brand)" fill-opacity="0.12" />
      <path v-if="thumb.fill" :d="thumb.fill" fill="#9DBCE9" />
      <path v-if="thumb.dash" :d="thumb.dash" fill="none" stroke="#98A2B3" stroke-width="1" stroke-dasharray="3 2" vector-effect="non-scaling-stroke" />
      <path v-if="thumb.stroke" :d="thumb.stroke" fill="none" stroke="var(--brand)" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" />
      <path v-if="thumb.accent" :d="thumb.accent" :fill="accent" />
    </svg>
  </div>
</template>
