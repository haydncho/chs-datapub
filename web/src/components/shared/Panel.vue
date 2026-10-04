<script setup lang="ts">
import StrokeIcon from '@/components/ui/stroke-icon/StrokeIcon.vue'
import { iconPaths } from '@/lib/icons'

/** 卡片:12px 圆角、细描边、柔和阴影;标题可带渐变图标块(icon),悬停轻微抬升。flush = 无内边距(内含表格时)。 */
defineProps<{ title?: string; sub?: string; flush?: boolean; icon?: string }>()
</script>

<template>
  <section class="panel rounded-2xl border border-line-soft bg-surface shadow-card" :class="flush ? '' : 'px-5 py-[18px]'">
    <div v-if="title || $slots.head" class="flex items-center gap-3" :class="flush ? 'px-5 pt-[18px] pb-3' : 'mb-3.5'">
      <span v-if="icon" class="panel-chip flex size-7 flex-none items-center justify-center rounded-lg text-white"><StrokeIcon :d="iconPaths[icon]" :size="15" /></span>
      <span v-if="title" :class="icon ? 'text-[14px] font-semibold text-ink' : 'sect-title'">{{ title }}</span>
      <span v-if="sub" class="text-[12px] text-ink-faint">{{ sub }}</span>
      <div class="ml-auto flex items-center gap-2"><slot name="head" /></div>
    </div>
    <slot />
  </section>
</template>

<style scoped>
.panel {
  transition: box-shadow 0.18s ease, border-color 0.18s ease;
}
.panel:hover {
  box-shadow: 0 8px 26px color-mix(in srgb, var(--c-primary) 10%, transparent);
}
.panel-chip {
  background: linear-gradient(135deg, var(--c-ic-blue-a), var(--c-ic-blue-b));
  box-shadow: 0 4px 10px color-mix(in srgb, var(--c-primary) 28%, transparent);
}
</style>
