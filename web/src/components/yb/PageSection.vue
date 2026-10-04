<script setup lang="ts">
import { cn } from '@/lib/utils'

/** Standard workbench page container: 1600px max, 24/32 padding, 16px vertical rhythm. Override via `class` (e.g. max-w-[1100px]). */
defineProps<{ label: string; class?: string }>()
</script>

<template>
  <section :data-screen-label="label" :class="cn('mx-auto flex w-full max-w-[1600px] flex-col gap-page px-8 pt-6 pb-14', $props.class)">
    <slot />
  </section>
</template>
