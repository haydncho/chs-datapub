<script setup lang="ts">
import { ref } from 'vue'
import { Button } from '@/components/ui/button'
import type { PageDef } from '@/lib/nav'
import ExportDialog from './ExportDialog.vue'

/** 标题行（睿衡页头）：13px 标题 + 弱化说明 + 右侧操作；可导出页面带「导出」（导出审批）。 */
defineProps<{ page: PageDef; title?: string }>()
const exportOpen = ref(false)
</script>

<template>
  <div class="flex flex-wrap items-end gap-x-6 gap-y-2 pb-1">
    <div class="min-w-0">
      <h1 class="text-[20px] leading-[1.3] font-semibold tracking-[.01em] text-ink" data-testid="page-title">{{ title ?? page.label }}</h1>
      <p class="mt-1 text-[13px] leading-[1.5] text-ink-muted">{{ page.summary }}</p>
    </div>
    <div class="ml-auto flex items-center gap-2.5">
      <slot name="actions" />
      <Button v-if="page.exportable" variant="outline" class="px-4 py-[7px]" data-testid="export-btn" @click="exportOpen = true">导出</Button>
    </div>
  </div>
  <ExportDialog v-if="page.exportable" v-model:open="exportOpen" :scope="page.key" />
</template>
