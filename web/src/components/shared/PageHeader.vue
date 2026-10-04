<script setup lang="ts">
import { ref } from 'vue'
import { Button } from '@/components/ui/button'
import StrokeIcon from '@/components/ui/stroke-icon/StrokeIcon.vue'
import { iconPaths } from '@/lib/icons'
import type { PageDef } from '@/lib/nav'
import ExportDialog from './ExportDialog.vue'

/** 标题行（睿衡页头）：13px 标题 + 弱化说明 + 右侧操作；可导出页面带「导出」（导出审批）。 */
defineProps<{ page: PageDef; title?: string }>()
const exportOpen = ref(false)
</script>

<template>
  <div class="flex flex-wrap items-end gap-x-6 gap-y-2 pb-1">
    <span class="ph-chip flex size-11 flex-none items-center justify-center rounded-2xl text-white"><StrokeIcon :d="iconPaths[page.icon]" :size="21" /></span>
    <div class="min-w-0">
      <div class="text-[12px] leading-none text-ink-faint">{{ page.group }}</div>
      <h1 class="mt-1 text-[22px] leading-[1.25] font-bold tracking-[.01em] text-ink" data-testid="page-title">{{ title ?? page.label }}</h1>
      <p class="mt-1 text-[13px] leading-[1.5] text-ink-muted">{{ page.summary }}</p>
    </div>
    <div class="ml-auto flex items-center gap-2.5">
      <slot name="actions" />
      <Button v-if="page.exportable" variant="outline" class="px-4 py-[7px]" data-testid="export-btn" @click="exportOpen = true">导出</Button>
    </div>
  </div>
  <ExportDialog v-if="page.exportable" v-model:open="exportOpen" :scope="page.key" />
</template>

<style scoped>
.ph-chip {
  background: linear-gradient(135deg, var(--c-ic-blue-a), var(--c-ic-blue-b));
  box-shadow: 0 8px 18px color-mix(in srgb, var(--c-primary) 30%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.35);
}
</style>
