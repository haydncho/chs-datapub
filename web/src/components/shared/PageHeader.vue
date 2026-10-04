<script setup lang="ts">
import { ref } from 'vue'
import { Button } from '@/components/ui/button'
import type { PageDef } from '@/lib/nav'
import ExportDialog from './ExportDialog.vue'

/** 刊头式页头:分组小标题(宋体字距)+ 大标题(宋体)+ 说明 + 双线;可导出页面带「导出」(导出审批)。 */
defineProps<{ page: PageDef; title?: string }>()
const exportOpen = ref(false)
</script>

<template>
  <div class="mast flex flex-wrap items-end gap-x-6 gap-y-2 pb-3">
    <div class="min-w-0">
      <div class="flex items-center gap-2 text-[12px] leading-none tracking-[.24em] text-[#C2371F]"><span class="h-px w-5 bg-[#C2371F]" />{{ page.group }}</div>
      <h1 class="mt-2 font-display text-[30px] leading-[1.2] font-bold tracking-[.03em] text-ink" data-testid="page-title">{{ title ?? page.label }}</h1>
      <p class="mt-1.5 text-[13px] leading-[1.6] text-ink-muted">{{ page.summary }}</p>
    </div>
    <div class="ml-auto flex items-center gap-2.5">
      <slot name="actions" />
      <Button v-if="page.exportable" variant="outline" class="px-4 py-[7px]" data-testid="export-btn" @click="exportOpen = true">导出</Button>
    </div>
  </div>
  <ExportDialog v-if="page.exportable" v-model:open="exportOpen" :scope="page.key" />
</template>

<style scoped>
.mast {
  border-bottom: 3px double var(--c-text);
}
</style>
