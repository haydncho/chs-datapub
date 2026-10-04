<script setup lang="ts">
import { cn } from '@/lib/utils'
import type { A8IconKey, A8IndicatorStatus } from '@/mock/A8'
import { useA8 } from './store'

const s = useA8()

const IP: Record<A8IconKey, string> = {
  ind: 'M4 20V11M10 20V5M16 20v-6M21 20H3',
  rpt: 'M6 3h9l5 5v13H6zM14 3v6h6',
  txt: 'M4 6h16M4 12h16M4 18h10',
  qa: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
  card: 'M3 5h18v14H3zM3 10h18',
}
const GC = { 钱: 'bg-brand', 效: 'bg-violet', 错: 'bg-bad' } as const
const ST: Record<A8IndicatorStatus, [string, string]> = {
  on: ['纳入', 'bg-ok-soft text-ok-ink'],
  hold: ['本期暂缓', 'bg-warn-soft text-warn-ink'],
  ex: ['仅内部 · 排除', 'bg-line-3 text-ink-4'],
}
const COLS = 'grid grid-cols-[minmax(0,1fr)_80px_120px_100px_90px] gap-3 px-3.5 py-[9px]'
</script>

<template>
  <div class="grid grid-cols-5 gap-2.5">
    <div
      v-for="k in s.d.packageCards"
      :key="k.label"
      class="relative overflow-hidden rounded-xl border border-[#DCE6F8] bg-[linear-gradient(135deg,var(--brand-soft)_0%,#fff_64%)] p-3.5"
    >
      <div class="flex items-start justify-between gap-1.5">
        <span class="min-w-0 truncate text-xs text-ink-4" :title="k.label">{{ k.label }}</span>
        <span class="flex size-[26px] shrink-0 items-center justify-center rounded-lg bg-white shadow-[0_0_0_1px_#DCE6F8]">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="var(--brand)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="IP[k.icon]" /></svg>
        </span>
      </div>
      <div class="yb-num text-[28px] font-semibold whitespace-nowrap">{{ k.value }}<span class="ml-[3px] text-[13px] text-ink-4">{{ k.unit }}</span></div>
      <div :class="cn('truncate text-xs', k.tone === 'ok' ? 'text-ok-ink' : 'text-ink-3')" :title="k.sub">{{ k.sub }}</div>
    </div>
  </div>
  <div class="mt-4 overflow-hidden rounded-[10px] border border-line-2">
    <div :class="cn(COLS, 'bg-surface-1 text-xs text-ink-4')">
      <span>发布指标</span><span>分组</span><span>对标档位</span><span>本期数据</span><span>状态</span>
    </div>
    <div
      v-for="r in s.d.indicators"
      :key="r.name"
      :class="cn(COLS, 'items-center border-t border-line-3 text-[13px]', r.status === 'ex' && 'opacity-60')"
    >
      <span class="overflow-hidden text-ellipsis whitespace-nowrap">{{ r.name }}</span>
      <span class="flex items-center gap-[5px] text-xs"><span :class="cn('size-[7px] rounded-full', GC[r.group])" />{{ r.group }}</span>
      <span class="text-xs whitespace-nowrap text-ink-3">{{ r.benchmark }}</span>
      <span :class="cn('text-xs whitespace-nowrap', r.period === '数据待到' ? 'text-warn-ink' : 'text-ink-3')">{{ r.period }}</span>
      <span :class="cn('justify-self-start rounded-full px-2 py-0.5 text-[11px] whitespace-nowrap', ST[r.status][1])">{{ ST[r.status][0] }}</span>
    </div>
  </div>
</template>
