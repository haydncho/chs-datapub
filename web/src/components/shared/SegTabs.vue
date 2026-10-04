<script setup lang="ts" generic="T extends string | number">
/** 分段页签（睿衡治理中心样式）：外框圆角描边，选中段主色浅底。 */
defineProps<{ items: { value: T; label: string; count?: number }[]; size?: 'sm' | 'md' }>()
const model = defineModel<T>({ required: true })
</script>

<template>
  <div class="inline-flex flex-none items-center gap-0.5 rounded-[10px] border border-line bg-surface p-1" role="tablist">
    <button
      v-for="it in items"
      :key="String(it.value)"
      type="button"
      role="tab"
      :aria-selected="model === it.value"
      class="flex cursor-pointer items-center gap-1.5 rounded-lg whitespace-nowrap transition-colors"
      :class="[
        size === 'sm' ? 'px-2.5 py-[3px] text-[11px]' : 'px-3.5 py-[5px] text-[13px]',
        model === it.value ? 'bg-primary-tint font-semibold text-primary' : 'text-ink-sub hover:bg-hover',
      ]"
      @click="model = it.value"
    >
      {{ it.label }}
      <span
        v-if="it.count !== undefined"
        class="rounded-full px-1.5 text-[11px] leading-[18px]"
        :class="model === it.value ? 'bg-primary-soft text-primary' : 'bg-chip text-ink-muted'"
      >{{ it.count }}</span>
    </button>
  </div>
</template>
