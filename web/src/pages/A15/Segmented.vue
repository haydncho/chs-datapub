<script setup lang="ts">
import { cn } from '@/lib/utils'

/** Bordered segmented control with dark selected segment (A15). */
defineProps<{ options: string[] }>()
const model = defineModel<number>({ required: true })
</script>

<template>
  <div class="flex w-max overflow-hidden rounded-lg border border-line-1">
    <button
      v-for="(l, i) in options"
      :key="l"
      type="button"
      :class="cn(
        'cursor-pointer px-4 py-1.5 text-xs whitespace-nowrap',
        model === i ? 'bg-ink-1 text-white' : 'bg-white text-ink-3 hover:bg-surface-1',
      )"
      @click="model = i"
    >{{ l }}</button>
  </div>
</template>
