<script setup lang="ts">
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import type { CommentView } from './SidePanel.vue'

/** 导出为意见单: preview table of all comments, 下载 Excel, 推送至意见与申诉. */
defineProps<{
  rows: CommentView[]
  /** not handled and not yet pushed: what 推送 would send */
  pending: number
  docNo: string
  subtitle: string
  title: string
  footNote: string
  /** false for the 委托分析 identity: 意见单 holds named, unreviewed detail */
  canExport: boolean
  busy?: boolean
}>()
const open = defineModel<boolean>('open', { required: true })
defineEmits<{ excel: []; push: [] }>()

const COLS = 'grid grid-cols-[44px_150px_120px_minmax(0,1fr)_90px] gap-3 px-4'
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      overlay-class="z-[96] bg-[rgba(11,21,38,.4)]"
      :show-close="false"
      class="z-[96] flex max-h-[calc(100dvh-32px)] w-[min(920px,calc(100%-32px))] flex-col overflow-y-auto gap-4 rounded-2xl border-0 bg-white px-[26px] py-6 shadow-[0_24px_64px_rgba(11,21,38,.3)]"
    >
      <div class="flex items-start justify-between gap-4">
        <div>
          <DialogDescription class="text-xs text-ink-4">{{ subtitle }}</DialogDescription>
          <DialogTitle class="text-xl font-semibold">{{ title }}</DialogTitle>
        </div>
        <span class="font-mono text-xs whitespace-nowrap text-ink-5">{{ docNo }}</span>
      </div>
      <div class="overflow-x-auto rounded-xl border border-line-1"><div class="min-w-[640px]">
        <div :class="[COLS, 'bg-surface-1 py-2.5 text-xs text-ink-4']">
          <span>序号</span><span>段落</span><span>提出人</span><span>意见内容</span><span>状态</span>
        </div>
        <div v-for="(r, i) in rows" :key="r.k" :class="[COLS, 'items-start border-t border-line-3 py-[11px] text-[13px]']">
          <span class="yb-num text-ink-5">{{ i + 1 }}</span>
          <span class="whitespace-nowrap text-brand">§ {{ r.sec }}</span>
          <span class="whitespace-nowrap">{{ r.who }}<div class="text-[11px] text-ink-5">{{ r.role }}</div></span>
          <span class="leading-[1.6]">{{ r.text }}</span>
          <Badge :variant="r.resolved ? 'ok' : r.pushedAs ? 'brand' : 'warn'" class="justify-self-start px-2 py-0.5 text-[11px] font-normal whitespace-nowrap">{{ r.resolved ? '已处理' : r.pushedAs ? '已推送' : '待处理' }}</Badge>
        </div>
      </div></div>
      <div class="flex flex-wrap items-center gap-2">
        <span class="min-w-full flex-1 text-xs text-ink-4 max-xl:min-w-0 max-xl:basis-full">共 {{ rows.length }} 条 · 待推送 {{ pending }} 条 · {{ footNote }}</span>
        <span v-if="!canExport" class="min-w-full flex-1 text-xs text-warn-ink max-xl:min-w-0 max-xl:basis-full">受控分析环境仅可导出审核后的聚合结果;意见单含机构具名明细且未经审核,请由行政管理组导出。</span>
        <Button variant="outline" class="h-9 px-4 font-normal max-xl:h-11" @click="open = false">关闭</Button>
        <Button variant="outline" class="h-9 px-4 font-normal max-xl:h-11" :disabled="!canExport || busy || rows.length === 0" @click="$emit('excel')">下载 Excel(CSV)</Button>
        <Button class="h-9 px-[18px] max-xl:h-11" :disabled="busy || pending === 0" @click="$emit('push')">{{ pending === 0 ? '无待推送批注' : '推送 ' + pending + ' 条至意见与申诉' }}</Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
