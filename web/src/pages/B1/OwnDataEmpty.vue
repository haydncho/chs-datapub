<script setup lang="ts">
/** Empty state for the institution pages when the viewer's organisation has no data on record. */
defineProps<{ note?: string; detail?: string }>()
</script>

<template>
  <div class="yb-card flex flex-col items-center gap-2 px-6 py-14 text-center" data-testid="own-data-empty" role="status">
    <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="var(--ink-5)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M4 20V10M10 20V4M16 20v-7M21 20H3" />
    </svg>
    <div class="text-[15px] font-semibold">{{ note || '暂无本院数据' }}</div>
    <div class="max-w-[440px] text-[13px] text-ink-4">
      {{ detail || '本期尚未向本机构定向发布该项数据。数据发布后将在此显示;如有疑问请联系示例市医保局数据工作组。' }}
    </div>
  </div>
</template>
