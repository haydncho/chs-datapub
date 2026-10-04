<script setup lang="ts">
import { cn } from '@/lib/utils'
import type { B4Data, B4Report } from '@/mock/B4'

/** A4-paper style preview: cover with KPIs + TOC, then body page 2. */
defineProps<{ report: B4Report; data: B4Data }>()
</script>

<template>
  <div class="flex w-full max-w-[720px] flex-col gap-4">
    <!-- cover -->
    <div class="relative flex min-h-[820px] flex-col overflow-hidden rounded-md bg-white px-[60px] pt-14 pb-10 shadow-[0_2px_10px_rgba(15,23,42,.08)]">
      <div class="text-xs tracking-[2px] text-ink-4">{{ data.issuer }}</div>
      <div class="mt-5 mb-7 h-[3px] w-14 bg-brand" />
      <div class="text-[30px] leading-[1.35] font-semibold text-pretty">{{ report.name }}</div>
      <div class="mt-2.5 text-sm text-ink-3">{{ data.audience }}</div>
      <div class="mt-9 mb-8 grid grid-cols-[1.2fr_1fr_1fr] gap-[18px] rounded-lg bg-brand-tint px-[22px] py-5">
        <div v-for="k in data.coverKpis" :key="k.label">
          <div class="text-[11px] text-ink-4">{{ k.label }}</div>
          <div
            v-if="k.kind === 'num'"
            :class="cn('yb-num text-2xl font-semibold', k.tone === 'bad' && 'text-bad')"
          >{{ k.value }}</div>
          <div v-else class="mt-1 text-sm font-semibold">{{ k.value }}</div>
          <div class="text-[11px] whitespace-nowrap text-ink-3">{{ k.sub }}</div>
        </div>
      </div>
      <div class="flex-1" />
      <div class="mb-2 text-xs text-ink-4">目录</div>
      <div
        v-for="t in data.toc"
        :key="t.n"
        class="flex items-baseline gap-2.5 border-b border-line-3 py-[7px] text-sm"
      >
        <span class="w-5 text-ink-5">{{ t.n }}</span>
        <span class="flex-1">{{ t.label }}</span>
        <span class="yb-num text-ink-5">{{ t.page }}</span>
      </div>
      <div class="mt-7 flex justify-between text-[11px] text-ink-5">
        <span>发布 2026-{{ report.date }} · {{ report.version }}</span>
        <span>{{ data.footerNote }}</span>
      </div>
    </div>

    <!-- body page -->
    <div class="flex flex-col gap-3.5 rounded-md bg-white px-[60px] py-11 shadow-[0_2px_10px_rgba(15,23,42,.08)]">
      <div class="text-xs text-ink-5">{{ data.overview.page }}</div>
      <div class="text-lg font-semibold">{{ data.overview.title }}</div>
      <div class="grid grid-cols-3 gap-2.5">
        <div
          v-for="s in data.overview.stats"
          :key="s.label"
          :class="cn('rounded-lg p-3', s.tone === 'bad' ? 'bg-bad-soft' : 'bg-surface-1')"
        >
          <div class="text-[11px] text-ink-4">{{ s.label }}</div>
          <div :class="cn('yb-num text-[22px] font-semibold', s.tone === 'bad' && 'text-bad')">{{ s.value }}</div>
        </div>
      </div>
      <div class="text-sm leading-[1.9] text-pretty text-ink-2">{{ data.overview.text }}</div>
    </div>
  </div>
</template>
