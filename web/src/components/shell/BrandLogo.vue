<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 30 })
</script>

<template>
  <!-- 品牌印章:朱砂方章 + 宋体「医」(正式标识确定后替换) -->
  <div
    class="flex flex-none items-center justify-center rounded-[3px] bg-[#C2371F] font-display font-bold text-[#FBF3E4] shadow-[inset_0_0_0_2px_rgba(251,243,228,.35)]"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.56)}px` }"
    aria-hidden="true"
  >医</div>
</template>
