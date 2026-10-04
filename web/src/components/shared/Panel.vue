<script setup lang="ts">
/** 卡片:扁平、1px 墨线框、直角;标题为宋体 + 左侧朱砂短竖;icon 属性保留兼容(不再渲染图标块)。flush = 无内边距(内含表格时)。 */
defineProps<{ title?: string; sub?: string; flush?: boolean; icon?: string }>()
</script>

<template>
  <section class="panel border border-line-strong bg-surface" :class="flush ? '' : 'px-5 py-[18px]'">
    <div v-if="title || $slots.head" class="flex items-center gap-3 border-b border-line pb-2.5" :class="flush ? 'mx-5 mt-[18px] mb-3' : 'mb-3.5'">
      <span v-if="title" class="font-display text-[15px] font-bold tracking-[.05em] text-ink">{{ title }}</span>
      <span v-if="sub" class="text-[12px] text-ink-faint">{{ sub }}</span>
      <div class="ml-auto flex items-center gap-2"><slot name="head" /></div>
    </div>
    <slot />
  </section>
</template>
