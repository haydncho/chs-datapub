<script setup lang="ts">
import { cn } from '@/lib/utils'

/** White card with optional 14px semibold title row and right-side meta. */
defineProps<{ title?: string; meta?: string; class?: string; bodyClass?: string }>()
</script>

<template>
  <div :class="cn('yb-card px-card-x py-card-y-sm', $props.class)">
    <div v-if="title || $slots.title || meta || $slots.meta" class="mb-3 flex items-center justify-between gap-3">
      <span class="text-sm font-semibold"><slot name="title">{{ title }}</slot></span>
      <span class="text-xs text-ink-4"><slot name="meta">{{ meta }}</slot></span>
    </div>
    <div :class="bodyClass"><slot /></div>
  </div>
</template>
