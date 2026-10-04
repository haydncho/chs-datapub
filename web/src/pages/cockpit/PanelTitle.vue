<script setup lang="ts">
withDefaults(defineProps<{ title: string; bar: string; size?: 'md' | 'lg' }>(), { size: 'md' })
</script>

<template>
  <div class="flex items-center gap-2.5">
    <span :class="['w-1 rounded-[2px]', size === 'lg' ? 'h-5' : 'h-[18px]']" :style="{ background: bar }" />
    <span :class="['font-semibold', size === 'lg' ? 'text-[22px]' : 'text-xl']">{{ title }}</span>
  </div>
</template>
