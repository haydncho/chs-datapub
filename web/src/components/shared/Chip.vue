<script setup lang="ts">
/** 芯片（单选 / 多选）：选中为主色浅底 + 主色字。 */
defineProps<{ on: boolean; disabled?: boolean }>()
defineEmits<{ click: [] }>()
</script>

<template>
  <button
    type="button"
    :disabled="disabled"
    class="cursor-pointer rounded-[3px] border px-3 py-[3px] text-[12px] whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-50"
    :class="on ? 'border-primary-line-strong bg-primary-tint font-semibold text-primary' : 'border-line bg-surface text-ink-sub hover:bg-hover'"
    :aria-pressed="on"
    @click="$emit('click')"
  >
    <slot />
  </button>
</template>
