<script setup lang="ts">
/** 卡片：10px 圆角、细描边、表面色；可选区块标题（3px 主色竖条）。flush = 无内边距（内含表格时）。 */
defineProps<{ title?: string; sub?: string; flush?: boolean }>()
</script>

<template>
  <section class="rounded-[10px] border border-line bg-surface" :class="flush ? '' : 'px-[18px] py-4'">
    <div v-if="title || $slots.head" class="flex items-center gap-3" :class="flush ? 'px-[18px] pt-4 pb-3' : 'mb-3'">
      <span v-if="title" class="sect-title">{{ title }}</span>
      <span v-if="sub" class="text-[12px] text-ink-faint">{{ sub }}</span>
      <div class="ml-auto flex items-center gap-2"><slot name="head" /></div>
    </div>
    <slot />
  </section>
</template>
