<script setup lang="ts">
import type { PortalReportDetail, ReportBlock } from '@/api/portalReports'

/**
 * A4 纸样预览（公文纸 data-paper：深色主题下仍白底黑字）。
 * 页眉「定向发布 · 仅限本院」；斜铺实名水印（姓名 机构 水印编号，透明度 0.12）；已更正版本右上斜角「已更正」丝带。
 */
defineProps<{ report: PortalReportDetail }>()
const diffTone = (v: string) => (v.startsWith('+') ? 'text-danger' : v.startsWith('−') || v.startsWith('-') ? 'text-success' : 'text-ink')
/** 病组表：编码 | 病组 | 病例 | 例均差额；其他表：首列加宽。 */
const cols = (b: ReportBlock) => {
  const n = (b.head ?? b.rows?.[0] ?? []).length
  return b.diff && n === 4 ? '60px minmax(0,1fr) 70px 80px' : `minmax(0,2fr) repeat(${n - 1}, minmax(0,1fr))`
}
const WM_CELLS = 24
</script>

<template>
  <div
    data-paper
    class="relative w-full max-w-[600px] overflow-hidden bg-white px-10 pt-9 pb-10 text-ink shadow-[0_2px_8px_rgba(0,0,0,.08)]"
    style="min-height: 560px"
    data-testid="report-paper"
  >
    <div
      v-if="report.status === 'OLD' || report.status === 'WITHDRAWN'"
      class="absolute top-[18px] right-[-34px] rotate-[35deg] px-10 py-[3px] text-[12px] text-white"
      :style="{ background: report.status === 'WITHDRAWN' ? 'var(--c-danger-solid)' : 'var(--c-muted-solid)' }"
      data-testid="corrected-ribbon"
    >{{ report.status === 'WITHDRAWN' ? '已撤回' : '已更正' }}</div>
    <div class="text-[11px] text-ink-faint" data-testid="paper-header">{{ report.header }}</div>
    <div class="mt-3.5 text-[20px] leading-[1.4] font-semibold">{{ report.title }}</div>
    <div class="mt-3 mb-4 h-px bg-primary" />
    <template v-for="(b, i) in report.body" :key="i">
      <div class="text-[13px] font-semibold" :class="i ? 'mt-3.5 mb-1.5' : 'mb-1.5'">{{ b.h }}</div>
      <div v-if="b.p" class="indent-[2em] text-[12px] leading-[1.9] text-ink-body">{{ b.p }}</div>
      <div v-if="b.rows" class="grid border-t border-line text-[12px]" :style="{ gridTemplateColumns: cols(b) }">
        <template v-if="b.head">
          <span v-for="(h, j) in b.head" :key="'h' + j" class="py-[5px] text-ink-muted" :class="j >= (b.diff ? 2 : 1) ? 'text-right' : ''">{{ h }}</span>
        </template>
        <template v-for="(r, k) in b.rows" :key="k">
          <span
            v-for="(c, j) in r"
            :key="j"
            class="py-[5px]"
            :class="[j >= (b.diff ? 2 : 1) ? 'text-right' : '', b.diff && j === r.length - 1 ? diffTone(c) : '']"
          >{{ c }}</span>
        </template>
      </div>
    </template>
    <div class="mt-6 text-right text-[11px] text-ink-faint">水印编号 <span class="font-mono">{{ report.wmNo }}</span> · 共 {{ report.pages }} 页 · 预览第 1 页</div>

    <!-- 斜铺实名水印：不拦截点击 -->
    <div
      class="pointer-events-none absolute inset-0 grid grid-cols-3 overflow-hidden text-ink"
      style="grid-auto-rows: 120px; opacity: 0.12"
      aria-hidden="true"
      data-testid="paper-watermark"
      :data-text="report.watermark"
    >
      <div v-for="n in WM_CELLS" :key="n" class="flex items-center justify-center">
        <span class="rotate-[-28deg] text-[13px] whitespace-nowrap">{{ report.watermark }}</span>
      </div>
    </div>
  </div>
</template>
