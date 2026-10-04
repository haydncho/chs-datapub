<script setup lang="ts">
/** 卡片：10px 圆角、细描边、表面色；可选区块标题（3px 主色竖条）。flush = 无内边距（内含表格时）。 */
defineProps<{ title?: string; sub?: string; flush?: boolean }>()
</script>

<template>
  <section class="rounded-xl border border-line-soft bg-surface shadow-card" :class="flush ? '' : 'px-5 py-[18px]'">
    <div v-if="title || $slots.head" class="flex items-center gap-3" :class="flush ? 'px-5 pt-[18px] pb-3' : 'mb-3.5'">
      <span v-if="title" class="sect-title">{{ title }}</span>
      <span v-if="sub" class="text-[12px] text-ink-faint">{{ sub }}</span>
      <div class="ml-auto flex items-center gap-2"><slot name="head" /></div>
    </div>
    <slot />
  </section>
</template>
