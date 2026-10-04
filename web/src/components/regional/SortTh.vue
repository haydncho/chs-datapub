<script setup lang="ts">
/**
 * 可排序表头单元格：当前排序列主色 + ↑/↓；其他可排序列显示弱化的 ↕ 提示可点击。
 * 键盘可达（button），aria-sort 标注当前方向。
 */
const props = defineProps<{ active: boolean; dir: 'asc' | 'desc'; align?: 'left' | 'right' }>()
defineEmits<{ sort: [] }>()
const ariaSort = () => (props.active ? (props.dir === 'asc' ? 'ascending' : 'descending') : 'none')
</script>

<template>
  <th :class="align === 'right' ? 'text-right' : ''" :aria-sort="ariaSort()">
    <button
      type="button"
      class="inline-flex cursor-pointer items-center gap-1 whitespace-nowrap hover:text-primary"
      :class="active ? 'text-primary' : ''"
      @click="$emit('sort')"
    >
      <slot />
      <span v-if="active" class="text-[11px]" data-sort-arrow>{{ dir === 'asc' ? '↑' : '↓' }}</span>
      <span v-else class="text-[11px] text-ink-ghost">↕</span>
    </button>
  </th>
</template>
