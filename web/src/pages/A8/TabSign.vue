<script setup lang="ts">
import { computed } from 'vue'
import { G } from '@/lib/palette'
import { useA8 } from './store'

const s = useA8()

const pct = computed(() => (s.posted ? Math.round((s.sigN / Math.max(1, s.covN)) * 100) + '%' : '0%'))
const dash = computed(() => (s.posted ? ((s.sigN / Math.max(1, s.covN)) * 97.4).toFixed(1) + ' 999' : '0 999'))
</script>

<template>
  <div v-if="!s.posted" class="px-5 py-10 text-center text-ink-4">
    <div class="font-semibold text-ink-2">尚未发布</div>
    <div class="mt-1 text-xs">召集人批准后,签收进度将在此实时追踪</div>
  </div>
  <div v-else class="grid grid-cols-[200px_minmax(0,1fr)] items-start gap-6 max-lg:grid-cols-1">
    <div class="flex flex-col items-center gap-2 max-lg:mx-auto">
      <div class="relative size-[140px]">
        <svg viewBox="0 0 36 36" class="size-[140px] -rotate-90">
          <circle cx="18" cy="18" r="15.5" fill="none" stroke="#EEF1F5" stroke-width="3.5" />
          <circle cx="18" cy="18" r="15.5" fill="none" :stroke="G" stroke-width="3.5" stroke-linecap="round" :stroke-dasharray="dash" />
        </svg>
        <div class="absolute inset-0 flex flex-col items-center justify-center">
          <span class="yb-num text-[30px] font-semibold">{{ pct }}</span>
          <span class="text-[11px] text-ink-4">已签收</span>
        </div>
      </div>
      <div class="text-xs whitespace-nowrap text-ink-3">{{ s.sigN }} / {{ s.covN }} 家 · {{ s.d.signRemain }}</div>
    </div>
    <div class="flex flex-col overflow-hidden rounded-[10px] border border-line-2">
      <div class="flex items-center justify-between bg-surface-1 px-3.5 py-2.5">
        <span class="text-xs text-ink-4">未签收机构 · {{ s.covN - s.sigN }} 家</span>
        <button type="button" class="cursor-pointer text-xs font-semibold text-brand max-xl:min-h-10 max-xl:px-2" @click="s.urgeAll()">一键催办</button>
      </div>
      <div
        v-for="(u, i) in s.unsigned"
        :key="u.name"
        class="grid grid-cols-[minmax(0,1fr)_90px_100px_72px] items-center gap-3 border-t border-line-3 px-3.5 py-[9px] text-[13px]"
      >
        <span class="overflow-hidden text-ellipsis whitespace-nowrap">{{ u.name }}</span>
        <span class="text-xs whitespace-nowrap text-ink-4">{{ s.d.tiers[u.tier]?.name }}</span>
        <span :class="['text-xs whitespace-nowrap', i < 2 ? 'text-warn-ink' : 'text-ink-5']">{{ i < 2 ? '已查阅 · 未签收' : '未查阅' }}</span>
        <button type="button"
          :class="['text-right text-xs whitespace-nowrap max-xl:min-h-10', s.urged[u.name] ? 'cursor-default text-ink-5' : 'cursor-pointer text-brand']"
          @click="s.urge(u.name)"
        >{{ s.urged[u.name] ? '已催办' : '催办' }}</button>
      </div>
    </div>
  </div>
</template>
