<script setup lang="ts">
import { cn } from '@/lib/utils'
import type { A7Advice } from '@/mock/A7'

/** §07 医保侧与医院侧建议: two side-by-side recommendation cards. */
defineProps<{ advice: A7Advice[] }>()
</script>

<template>
  <div class="grid grid-cols-2 gap-3 text-[13px] leading-[1.8]">
    <div
      v-for="a in advice"
      :key="a.side"
      :class="cn('rounded-[10px] px-4 py-3.5', a.tone === 'brand' ? 'bg-brand-tint' : 'bg-[#F4FBF7]')"
    >
      <div :class="cn('mb-1 font-semibold', a.tone === 'brand' ? 'text-brand' : 'text-ok-ink')">{{ a.side }}</div>
      <div v-for="t in a.items" :key="t">{{ t }}</div>
    </div>
  </div>
</template>
