<script setup lang="ts">
/** 横条（浅底轨道 + 实色条）：width 为 0–100 的百分比，color 取语义令牌。 */
withDefaults(defineProps<{ width: number; color?: string; height?: number }>(), { color: 'var(--c-primary-solid)', height: 12 })
</script>

<template>
  <div class="rounded-[3px] bg-[var(--c-chip)]" :style="{ height: `${height}px` }">
    <div class="rounded-[3px]" :style="{ width: `${Math.max(0, Math.min(100, width))}%`, height: `${height}px`, background: color }" />
  </div>
</template>
