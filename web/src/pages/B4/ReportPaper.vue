<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import type { B4Data, B4Report, B4ReportContent } from '@/mock/B4'

/** A4-paper style preview: cover with KPIs + TOC, then body page 2 — of the selected report. */
const props = defineProps<{ report: B4Report; data: B4Data }>()

const c = computed<B4ReportContent & { audience: string }>(() => {
  const own = props.data.contents?.[props.report.name]
  return {
    audience: own?.audience ?? props.data.audience,
    coverKpis: own?.coverKpis ?? props.data.coverKpis,
    toc: own?.toc ?? props.data.toc,
    overview: own?.overview ?? props.data.overview,
  }
})
</script>

<template>
  <div class="flex w-full max-w-[720px] min-w-0 flex-col gap-4">
    <!-- cover -->
    <div class="relative flex min-h-[820px] flex-col overflow-hidden rounded-md bg-white px-[60px] pt-14 pb-10 shadow-[0_2px_10px_rgba(15,23,42,.08)] max-md:min-h-0 max-md:px-5 max-md:pt-8 max-md:pb-6">
      <div class="text-xs tracking-[2px] text-ink-4">{{ data.issuer }}</div>
      <div class="mt-5 mb-7 h-[3px] w-14 bg-brand" />
      <div class="text-[30px] leading-[1.35] font-semibold text-pretty max-md:text-2xl">{{ report.name }}</div>
      <div class="mt-2.5 text-sm text-ink-3">{{ c.audience }}</div>
      <div class="mt-9 mb-8 grid grid-cols-[1.2fr_1fr_1fr] gap-[18px] rounded-lg bg-brand-tint px-[22px] py-5 max-md:mt-6 max-md:mb-6 max-md:grid-cols-1 max-md:gap-3 max-md:px-4 max-md:py-4">
        <div v-for="k in c.coverKpis" :key="k.label" class="min-w-0">
          <div class="text-[11px] text-ink-4">{{ k.label }}</div>
          <div
            v-if="k.kind === 'num'"
            :class="cn('yb-num text-2xl font-semibold', k.tone === 'bad' && 'text-bad')"
          >{{ k.value }}</div>
          <div v-else class="mt-1 text-sm font-semibold">{{ k.value }}</div>
          <div class="text-[11px] whitespace-nowrap text-ink-3 max-md:whitespace-normal">{{ k.sub }}</div>
        </div>
      </div>
      <div class="flex-1" />
      <div class="mb-2 text-xs text-ink-4">目录</div>
      <div
        v-for="t in c.toc"
        :key="t.n"
        class="flex items-baseline gap-2.5 border-b border-line-3 py-[7px] text-sm"
      >
        <span class="w-5 text-ink-5">{{ t.n }}</span>
        <span class="flex-1">{{ t.label }}</span>
        <span class="yb-num text-ink-5">{{ t.page }}</span>
      </div>
      <div class="mt-7 flex justify-between gap-3 text-[11px] text-ink-5 max-md:flex-col max-md:gap-1">
        <span>发布 2026-{{ report.date }} · {{ report.version }}</span>
        <span>{{ data.footerNote }}</span>
      </div>
    </div>

    <!-- body page -->
    <div class="flex flex-col gap-3.5 rounded-md bg-white px-[60px] py-11 shadow-[0_2px_10px_rgba(15,23,42,.08)] max-md:px-5 max-md:py-6">
      <div class="text-xs text-ink-5">{{ c.overview.page }}</div>
      <div class="text-lg font-semibold">{{ c.overview.title }}</div>
      <div class="grid grid-cols-3 gap-2.5 max-sm:grid-cols-1">
        <div
          v-for="s in c.overview.stats"
          :key="s.label"
          :class="cn('rounded-lg p-3', s.tone === 'bad' ? 'bg-bad-soft' : 'bg-surface-1')"
        >
          <div class="text-[11px] text-ink-4">{{ s.label }}</div>
          <div :class="cn('yb-num text-[22px] font-semibold', s.tone === 'bad' && 'text-bad')">{{ s.value }}</div>
        </div>
      </div>
      <div class="text-sm leading-[1.9] text-pretty text-ink-2">{{ c.overview.text }}</div>
    </div>
  </div>
</template>
