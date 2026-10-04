<script setup lang="ts">
/**
 * Page title row: 24px title + grey subtitle on the left, actions on the right.
 * Slots: `title` (title plus inline badges), `subtitle`, default (actions).
 */
defineProps<{ title?: string; subtitle?: string }>()
</script>

<template>
  <div class="flex items-end justify-between gap-5">
    <div class="min-w-0 flex-1">
      <div class="flex items-center gap-2.5 text-2xl font-semibold">
        <slot name="title"><span class="tracking-[-0.2px]">{{ title }}</span></slot>
      </div>
      <div v-if="subtitle || $slots.subtitle" class="mt-0.5 text-[13px] text-ink-4">
        <slot name="subtitle">{{ subtitle }}</slot>
      </div>
    </div>
    <div v-if="$slots.default" class="flex shrink-0 items-center gap-2">
      <slot />
    </div>
  </div>
</template>
