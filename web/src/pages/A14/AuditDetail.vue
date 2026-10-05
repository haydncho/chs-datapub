<script setup lang="ts">
import type { A14EventType, A14Log } from '@/mock/A14'

/** Right-hand detail card of A14: terminal, watermark, chain signature, config diff, anomaly. */
const props = defineProps<{ log: A14Log; date: string; kindClass: Record<A14EventType, string> }>()

/** live rows carry their own date (time column may read "昨天 17:40" / "09-28 17:40") */
function when(l: A14Log) {
  return l.date ? `${l.date} ${l.time.split(' ').pop()}` : `${props.date} ${l.time}`
}

function short(h: string) {
  return `${h.slice(0, 12)}…${h.slice(-8)}`
}
</script>

<template>
  <div class="yb-card xl:sticky xl:top-(--sticky-panel) flex flex-col gap-3 p-5">
    <div>
      <span :class="['rounded px-2 py-px text-[11px] font-semibold', kindClass[log.type]]">{{ log.type }}</span>
      <div class="mt-1.5 text-base font-semibold break-all">{{ log.object }}</div>
      <div class="text-xs text-ink-4">{{ log.who }} · {{ log.role }} · {{ when(log) }}</div>
    </div>
    <div class="grid grid-cols-[64px_minmax(0,1fr)] gap-1.5 text-xs">
      <template v-if="log.page">
        <span class="text-ink-4">页面</span><span class="font-mono">{{ log.page }} / {{ log.action }}</span>
      </template>
      <span class="text-ink-4">终端</span><span class="font-mono">{{ log.ip }} · {{ log.terminal }}</span>
      <span class="text-ink-4">水印</span><span class="font-mono">{{ log.watermark }}</span>
      <span class="text-ink-4">签名</span>
      <span :class="['font-mono', log.signatureOk ? 'text-ok-ink' : 'text-bad-ink']">{{ log.signatureOk ? '✓ 链式校验通过' : '✗ 链式校验失败' }}</span>
      <template v-if="log.hash">
        <span class="text-ink-4">前序哈希</span><span class="truncate font-mono text-ink-4" :title="log.prevHash">{{ short(log.prevHash ?? '') }}</span>
        <span class="text-ink-4">本条哈希</span><span class="truncate font-mono text-ink-4" :title="log.hash">{{ short(log.hash) }}</span>
      </template>
    </div>
    <div v-if="log.changes && log.changes.length" class="flex flex-col gap-1.5">
      <div v-for="c in log.changes" :key="c.field" class="overflow-hidden rounded-[10px] font-mono text-xs">
        <div class="bg-surface-1 px-2.5 py-1 text-[11px] text-ink-4">{{ c.label }}</div>
        <div class="bg-bad-soft px-2.5 py-1.5 text-bad-ink">− {{ c.from }}</div>
        <div class="bg-ok-soft px-2.5 py-1.5 text-ok-ink">+ {{ c.to }}</div>
      </div>
    </div>
    <div v-else-if="log.diff" class="overflow-hidden rounded-[10px] font-mono text-xs">
      <div class="bg-bad-soft px-2.5 py-1.5 text-bad-ink">− {{ log.diff.from }}</div>
      <div class="bg-ok-soft px-2.5 py-1.5 text-ok-ink">+ {{ log.diff.to }}</div>
    </div>
    <div v-if="log.risk" class="rounded-[10px] bg-warn-soft px-3 py-2.5 text-xs text-[#7A4510]">{{ log.riskNote ?? '异常:非工作时间批量查阅 · 已推送安全员' }}</div>
  </div>
</template>
