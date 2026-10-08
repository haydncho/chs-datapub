<script setup lang="ts">
import type { B4Data, B4Seg } from '@/mock/B4'

/** 已更正 report: v1 → v2 change table + side-by-side paragraph. */
defineProps<{ name: string; diff: B4Data['diff'] }>()

const segClass = (s: B4Seg) =>
  s.mark === 'del'
    ? 'rounded-[3px] bg-bad-soft px-[3px] text-bad-ink line-through'
    : s.mark === 'add'
      ? 'rounded-[3px] bg-ok-soft px-[3px] font-semibold text-ok-ink'
      : s.mark === 'note'
        ? 'rounded-[3px] bg-ok-soft px-[3px] text-ok-ink'
        : ''
</script>

<template>
  <div class="flex w-full max-w-[980px] flex-col gap-3.5">
    <div class="flex flex-wrap items-center gap-x-[18px] gap-y-2 rounded-xl border border-line-1 bg-white px-[22px] py-[18px]">
      <div class="min-w-0 flex-1 max-xl:basis-[240px]">
        <div class="text-xs text-ink-4">版本对比</div>
        <div class="text-lg font-semibold text-pretty xl:whitespace-nowrap">{{ name }}</div>
      </div>
      <div class="flex items-center gap-2.5 whitespace-nowrap">
        <span class="rounded-lg bg-bad-soft px-2.5 py-1 text-xs font-semibold text-bad-ink">{{ diff.from }}</span>
        <span class="text-ink-5">→</span>
        <span class="rounded-lg bg-ok-soft px-2.5 py-1 text-xs font-semibold text-ok-ink">{{ diff.to }}</span>
      </div>
      <span class="text-xs whitespace-nowrap text-ink-4">{{ diff.summary }}</span>
    </div>

    <div class="overflow-x-auto rounded-xl border border-line-1 bg-white">
    <div class="max-xl:min-w-[616px]">
      <div class="grid grid-cols-[96px_minmax(0,1fr)_88px_136px_96px] xl:grid-cols-[110px_minmax(0,1fr)_100px_130px_100px] 2xl:grid-cols-[150px_minmax(0,1fr)_140px_140px_150px] gap-3 px-4 xl:gap-3.5 xl:px-5 bg-surface-1 py-2.5 text-xs text-ink-4">
        <span>位置</span><span>内容</span><span class="text-right">v1</span><span class="text-right">v2</span><span>依据</span>
      </div>
      <div
        v-for="d in diff.rows"
        :key="d.item"
        class="grid grid-cols-[96px_minmax(0,1fr)_88px_136px_96px] xl:grid-cols-[110px_minmax(0,1fr)_100px_130px_100px] 2xl:grid-cols-[150px_minmax(0,1fr)_140px_140px_150px] items-center gap-3 px-4 xl:gap-3.5 xl:px-5 border-t border-line-3 py-3 text-[13px]"
      >
        <span class="text-xs whitespace-nowrap text-brand">{{ d.pos }}</span>
        <span>{{ d.item }}</span>
        <span class="yb-num text-right font-semibold whitespace-nowrap text-bad-ink line-through">{{ d.before }}</span>
        <span class="yb-num text-right font-semibold whitespace-nowrap text-ok-ink">{{ d.after }}</span>
        <span class="text-xs whitespace-nowrap text-ink-4">{{ d.why }}</span>
      </div>
    </div>
    </div>

    <div class="grid grid-cols-1 gap-3.5 md:grid-cols-2">
      <div class="rounded-xl border border-bad-line bg-white px-[22px] py-[18px]">
        <div class="mb-2 text-xs font-semibold text-bad-ink">v1 · {{ diff.section }}</div>
        <div class="text-sm leading-[1.9] text-ink-2">
          <span v-for="(s, i) in diff.before" :key="i" :class="segClass(s)">{{ s.t }}</span>
        </div>
      </div>
      <div class="rounded-xl border border-[#CBEBDB] bg-white px-[22px] py-[18px]">
        <div class="mb-2 text-xs font-semibold text-ok-ink">v2 · {{ diff.section }}</div>
        <div class="text-sm leading-[1.9] text-ink-2">
          <span v-for="(s, i) in diff.after" :key="i" :class="segClass(s)">{{ s.t }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
