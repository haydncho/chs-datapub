<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import { softChip, useA8, type SegBatch, type SegGroup } from './store'

const s = useA8()

const BATCH: SegBatch[] = ['全部', '第一批', '第二批']
const GRP: SegGroup[] = ['不限', '收治 BR25', '收治 GG19']
const CHIP = 'cursor-pointer rounded-lg border px-3 py-[5px] font-medium whitespace-nowrap max-xl:min-h-10'

const byTier = computed(() =>
  s.d.tiers.map((t, i) => {
    const c = s.coverage.filter(h => h.tier === i).length
    return { t: t.name, c, n: t.total, w: (c / t.total) * 100 + '%' }
  }),
)
const delta = computed(() => s.covN - s.d.lastCoverage)
const dTxt = computed(() => (delta.value === 0 ? '与上期一致' : (delta.value > 0 ? '+' : '−') + Math.abs(delta.value) + ' 较上期'))
const dC = computed(() => (delta.value === 0 ? 'text-ink-4' : delta.value > 0 ? 'text-ok-ink' : 'text-warn-ink'))
const missTxt = computed(
  () => s.missTiers.map(i => s.d.tiers[i]?.name).join('、') + ' 中有机构收治 BR25,但未纳入本次发布',
)
</script>

<template>
  <div v-if="s.scopeLocked" class="mb-3.5 rounded-[10px] bg-surface-1 px-3.5 py-2.5 text-xs leading-[1.7] text-ink-3">
    {{ s.posted && s.cur.scope ? '已按以下范围批准定向发布 · 范围已锁定' : s.step === 5 ? '定向范围由召集人在审批时确定 · 当前身份只读' : '任务进入召集人审批后可调整定向范围' }}
  </div>
  <div :class="cn('grid grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] gap-6 max-lg:grid-cols-1', s.scopeLocked && '[&_button]:pointer-events-none [&_button]:opacity-70')">
    <div class="grid grid-cols-[72px_1fr] items-center gap-x-2.5 gap-y-3 text-xs">
      <span class="text-ink-4">等级</span>
      <div class="flex flex-wrap gap-1.5">
        <button type="button"
          v-for="(t, i) in s.d.tiers"
          :key="t.name"
          :class="cn(CHIP, softChip(s.sTiers.includes(i)))"
          @click="s.toggle('sTiers', i)"
        >{{ t.name }}</button>
      </div>
      <span class="text-ink-4">县区</span>
      <div class="flex flex-wrap gap-1.5">
        <button type="button"
          v-for="x in s.d.districts"
          :key="x"
          :class="cn(CHIP, softChip(s.sDist.includes(x)))"
          @click="s.toggle('sDist', x)"
        >{{ x }}</button>
      </div>
      <span class="text-ink-4">付费批次</span>
      <div class="flex flex-wrap gap-1.5">
        <button type="button" v-for="x in BATCH" :key="x" :class="cn(CHIP, softChip(s.sBatch === x))" @click="!s.scopeLocked && (s.sBatch = x)">{{ x }}</button>
      </div>
      <span class="text-ink-4">收治病组</span>
      <div class="flex flex-wrap gap-1.5">
        <button type="button" v-for="x in GRP" :key="x" :class="cn(CHIP, softChip(s.sGrp === x))" @click="!s.scopeLocked && (s.sGrp = x)">{{ x }}</button>
      </div>
      <template v-if="s.missTiers.length">
        <span />
        <div class="rounded-[10px] border border-[#F6DFB8] bg-[#FFFBF4] px-3 py-2.5 leading-[1.6] text-[#7A4510]">
          可能遗漏:{{ missTxt }}<button type="button" class="ml-2 cursor-pointer font-semibold text-brand max-xl:min-h-10 max-xl:px-2" @click="s.fixMiss()">一键纳入</button>
        </div>
      </template>
    </div>
    <div class="rounded-xl bg-surface-2 px-[18px] py-4">
      <div class="flex items-baseline justify-between">
        <span class="text-xs text-ink-4">覆盖机构</span>
        <span :class="cn('yb-num text-xs font-semibold', dC)">{{ dTxt }}</span>
      </div>
      <div class="yb-num text-[44px] leading-[1.1] font-semibold text-brand">{{ s.covN }}<span class="text-base text-ink-4"> / {{ s.d.institutions.length }}</span></div>
      <div class="mt-2.5 flex flex-col gap-1.5">
        <div v-for="b in byTier" :key="b.t" class="grid grid-cols-[64px_1fr_44px] items-center gap-2 text-xs">
          <span class="text-ink-3">{{ b.t }}</span>
          <div class="h-1.5 rounded-[3px] bg-line-1">
            <div class="h-1.5 rounded-[3px] bg-brand transition-[width] duration-300 ease-out" :style="{ width: b.w }" />
          </div>
          <span class="yb-num text-right font-semibold">{{ b.c }}/{{ b.n }}</span>
        </div>
      </div>
    </div>
  </div>
  <div class="mt-3.5 text-xs leading-[1.7] text-ink-3">{{ s.covNames }}</div>
</template>
