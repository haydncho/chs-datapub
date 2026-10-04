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
  <div class="flex flex-wrap items-baseline gap-x-4 gap-y-2 border-b border-line pb-[9px]">
    <span class="text-[13px] font-semibold text-ink" data-testid="page-title">{{ title ?? page.label }}</span>
    <span class="text-[12px] text-ink-faint">{{ page.summary }}</span>
    <div class="ml-auto flex items-center gap-2.5">
      <slot name="actions" />
      <Button v-if="page.exportable" variant="outline" class="px-4 py-[7px]" data-testid="export-btn" @click="exportOpen = true">导出</Button>
    </div>
  </div>
  <ExportDialog v-if="page.exportable" v-model:open="exportOpen" :scope="page.key" />
</template>
