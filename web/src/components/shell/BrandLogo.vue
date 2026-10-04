<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 30 })
</script>

<template>
  <!-- 品牌标识：渐变圆角方块 + 白字「医」（正式标识确定后替换） -->
  <div
    class="flex flex-none items-center justify-center rounded-[9px] bg-[linear-gradient(135deg,var(--c-brand-a),var(--c-brand-b))] font-bold text-white shadow-[inset_0_1px_0_rgba(255,255,255,.25)]"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.5)}px` }"
    aria-hidden="true"
  >医</div>
</template>
