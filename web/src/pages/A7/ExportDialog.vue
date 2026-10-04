<script setup lang="ts">
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import type { CommentView } from './SidePanel.vue'

/** 导出为意见单: preview table of all comments, 下载 Excel, 推送至意见与申诉. */
defineProps<{ rows: CommentView[]; pending: number; docNo: string; subtitle: string; title: string; footNote: string }>()
const open = defineModel<boolean>('open', { required: true })
defineEmits<{ excel: []; push: [] }>()

const COLS = 'grid grid-cols-[44px_150px_120px_minmax(0,1fr)_90px] gap-3 px-4'
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      overlay-class="z-[96] bg-[rgba(11,21,38,.4)]"
      :show-close="false"
      class="z-[96] flex w-[min(920px,calc(100%-64px))] flex-col gap-4 rounded-2xl border-0 bg-white px-[26px] py-6 shadow-[0_24px_64px_rgba(11,21,38,.3)]"
    >
      <div class="flex items-start justify-between gap-4">
        <div>
          <DialogDescription class="text-xs text-ink-4">{{ subtitle }}</DialogDescription>
          <DialogTitle class="text-xl font-semibold">{{ title }}</DialogTitle>
        </div>
        <span class="font-mono text-xs whitespace-nowrap text-ink-5">{{ docNo }}</span>
      </div>
      <div class="overflow-hidden rounded-xl border border-line-1">
        <div :class="[COLS, 'bg-surface-1 py-2.5 text-xs text-ink-4']">
          <span>序号</span><span>段落</span><span>提出人</span><span>意见内容</span><span>状态</span>
        </div>
        <div v-for="(r, i) in rows" :key="r.k" :class="[COLS, 'items-start border-t border-line-3 py-[11px] text-[13px]']">
          <span class="yb-num text-ink-5">{{ i + 1 }}</span>
          <span class="whitespace-nowrap text-brand">§ {{ r.sec }}</span>
          <span class="whitespace-nowrap">{{ r.who }}<div class="text-[11px] text-ink-5">{{ r.role }}</div></span>
          <span class="leading-[1.6]">{{ r.text }}</span>
          <Badge :variant="r.resolved ? 'ok' : 'warn'" class="justify-self-start px-2 py-0.5 text-[11px] font-normal">{{ r.resolved ? '已处理' : '待处理' }}</Badge>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span class="flex-1 text-xs text-ink-4">共 {{ rows.length }} 条 · 待处理 {{ pending }} 条 · {{ footNote }}</span>
        <Button variant="outline" class="h-9 px-4 font-normal" @click="open = false">关闭</Button>
        <Button variant="outline" class="h-9 px-4 font-normal" @click="$emit('excel')">下载 Excel</Button>
        <Button class="h-9 px-[18px]" @click="$emit('push')">推送至意见与申诉</Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
