<script setup lang="ts">
import { computed } from 'vue'
import { G, R } from '@/lib/palette'
import type { B1Data } from '@/mock/B1'
import { vPress } from '@/lib/a11y'

/** 医保记账 vs DRG 支付 + 偏离贡献 (right column of B1). */
const props = defineProps<{ data: B1Data }>()
const selected = defineModel<string>('selected', { required: true })

const n1 = (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

const settle = computed(() => {
  const { billed, drgPaid } = props.data.settlement
  const dev = drgPaid - billed
  return {
    billed: n1(billed) + ' 万',
    paid: n1(drgPaid) + ' 万',
    paidW: (drgPaid / billed * 100).toFixed(1) + '%',
    dev: (dev < 0 ? '−' : '+') + n1(Math.abs(dev)) + ' 万',
    devPct: (dev < 0 ? '−' : '+') + Math.abs(dev / billed * 100).toFixed(1) + '%',
    neg: dev < 0,
  }
})

const top = computed(() => {
  const sorted = [...props.data.drgs].sort((a, b) => b.cases * b.diff - a.cases * a.diff)
  const t = [...sorted.slice(0, 4), ...sorted.slice(-2)]
  const tm = Math.max(...t.map(x => Math.abs(x.cases * x.diff)))
  return t.map(x => {
    const v = x.cases * x.diff
    return {
      k: x.code,
      n: x.name.split(',')[0],
      v: (v > 0 ? '+' : '−') + (Math.abs(v) / 10000).toFixed(1) + ' 万',
      w: (Math.abs(v) / tm * 100).toFixed(1) + '%',
      c: v > 0 ? R : G,
    }
  })
})
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="yb-card px-card-x py-card-y">
      <div class="mb-3.5 text-[15px] font-semibold">医保记账 vs DRG 支付</div>
      <div class="flex flex-col gap-2.5">
        <div>
          <div class="flex justify-between text-[12px]">
            <span class="text-ink-3">医保记账总额</span><span class="yb-num font-semibold">{{ settle.billed }}</span>
          </div>
          <div class="mt-1 h-3 rounded-[3px] bg-brand" />
        </div>
        <div>
          <div class="flex justify-between text-[12px]">
            <span class="text-ink-3">DRG 支付总额</span><span class="yb-num font-semibold">{{ settle.paid }}</span>
          </div>
          <div class="mt-1 h-3 rounded-[3px] bg-[#9DBCE9]" :style="{ width: settle.paidW }" />
        </div>
        <div class="flex items-baseline justify-between border-t border-line-2 pt-2.5">
          <span class="text-[12px] text-ink-3">偏离</span>
          <span>
            <span class="yb-num text-[24px] font-semibold" :class="settle.neg ? 'text-bad' : 'text-ok'">{{ settle.dev }}</span>
            <span class="text-[12px]" :class="settle.neg ? 'text-bad-ink' : 'text-ok-ink'"> · {{ settle.devPct }}</span>
          </span>
        </div>
      </div>
    </div>

    <div class="yb-card px-card-x py-card-y">
      <div class="mb-2.5 flex justify-between">
        <span class="text-[15px] font-semibold">偏离贡献</span><span class="text-[12px] text-ink-4">差额总额</span>
      </div>
      <div v-press
        v-for="t in top" :key="t.k"
        class="grid cursor-pointer grid-cols-[44px_minmax(0,1fr)_1fr_64px] items-center gap-2 py-1.5 text-[12px]"
        @click="selected = t.k"
      >
        <span class="yb-num font-semibold text-brand">{{ t.k }}</span>
        <span class="truncate" :title="t.n">{{ t.n }}</span>
        <div class="h-2 rounded bg-[#F4F6F9]"><div class="h-2 rounded" :style="{ width: t.w, background: t.c }" /></div>
        <span class="yb-num text-right font-semibold" :style="{ color: t.c }">{{ t.v }}</span>
      </div>
    </div>
  </div>
</template>
