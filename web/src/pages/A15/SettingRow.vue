<script setup lang="ts">
/** 统一的"设置行":左侧标题 + 说明,右侧控件;`stack` 时控件换到下一行(宽控件用)。 */
defineProps<{ title: string; desc?: string; stack?: boolean }>()
</script>

<template>
  <div :class="['border-t border-line-2 py-3.5 first:border-t-0 first:pt-0 last:pb-0', stack ? 'flex flex-col gap-3' : 'flex items-center justify-between gap-x-6 gap-y-2 max-xl:flex-wrap']">
    <div class="min-w-0" :class="stack ? '' : 'flex-1'">
      <div class="text-[13px] font-medium text-ink-1">{{ title }}</div>
      <div v-if="desc" class="mt-0.5 text-xs text-ink-4">{{ desc }}</div>
    </div>
    <div :class="stack ? '' : 'shrink-0'"><slot /></div>
  </div>
</template>
