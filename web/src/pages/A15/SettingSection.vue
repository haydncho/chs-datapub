<script setup lang="ts">
/** 设置分区卡片:左侧导航通过 id 定位;标题 + 一句话说明 + 若干 SettingRow。 */
defineProps<{ id: string; title: string; desc?: string }>()
</script>

<template>
  <section :id="id" :aria-labelledby="`${id}-t`" class="yb-card scroll-mt-[136px] max-xl:scroll-mt-[calc(var(--sticky-top)+130px)] px-card-x py-card-y">
    <h2 :id="`${id}-t`" class="text-[15px] font-semibold">{{ title }}</h2>
    <p v-if="desc" class="mt-0.5 mb-4 text-xs text-ink-4">{{ desc }}</p>
    <div v-else class="mb-4" />
    <slot />
  </section>
</template>
