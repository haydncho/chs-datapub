<script setup lang="ts">
/** 原型风格的线性图标：24×24 viewBox，currentColor 描边。 */
withDefaults(defineProps<{ d: string; size?: number; strokeWidth?: number }>(), {
  size: 16,
  strokeWidth: 1.9,
})
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="d" />
  </svg>
</template>
