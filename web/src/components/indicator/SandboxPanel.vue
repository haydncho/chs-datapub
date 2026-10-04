<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { indicatorApi, type SandboxResult } from '@/api/indicator'
import KpiCard from '@/components/shared/KpiCard.vue'
import Panel from '@/components/shared/Panel.vue'
import { notifyError } from '@/lib/notify'

/**
 * 算法沙盘:选择原子指标组合,拖动小样本抑制阈值,由分析引擎即时试算——
 * 哪些同级组被抑制(不输出分位)、各机构落在 P25 / 中位 / P75 的哪一侧、超 P75 的机构有哪些。
 * 无状态:不写草稿、不影响已上线指标;结果只在分析监测区内可见。
 */
const combos = ref<{ numerator: string; denominator: string }[]>([])
const pick = ref(0)
const minOrgs = ref(5)
const minCases = ref(30)
const res = ref<SandboxResult | null>(null)
const busy = ref(false)
let timer = 0

onMounted(async () => {
  try {
    combos.value = await indicatorApi.sandboxCombos()
    await run()
  } catch (e) {
    notifyError(e)
  }
})

async function run() {
  const c = combos.value[pick.value]
  if (!c) return
  busy.value = true
  try {
    res.value = await indicatorApi.sandbox({ numerator: c.numerator, denominator: c.denominator, minOrgs: minOrgs.value, minCases: minCases.value })
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}
watch([pick, minOrgs, minCases], () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(run, 220)
})

const all = computed(() => (res.value?.detail ?? []).flatMap((g) => g.orgs.map((o) => o.value)))
const lo = computed(() => Math.min(...all.value, 0))
const hi = computed(() => Math.max(...all.value, 1))
const x = (v: number) => ((v - lo.value) / (hi.value - lo.value || 1)) * 100
const suppressedN = computed(() => (res.value?.groups ?? []).filter((g) => g.suppressed).length)
const flagged = computed(() =>
  (res.value?.detail ?? []).flatMap((g) => {
    const st = res.value!.groups.find((s) => s.name === g.name)
    return st && !st.suppressed && st.p75 != null ? g.orgs.filter((o) => o.value > st.p75!).map((o) => ({ ...o, group: g.name })) : []
  }),
)
const spark = computed(() => (res.value?.groups ?? []).map((g) => g.n))
const stat = (name: string) => res.value?.groups.find((g) => g.name === name)
const comboName = (c: { numerator: string; denominator: string }) => `${c.numerator} ÷ ${c.denominator}`
</script>

<template>
  <div class="flex flex-col gap-3.5" data-testid="sandbox">
    <div class="grid grid-cols-4 gap-3.5">
      <KpiCard label="同级组" :value="String(res?.groups.length ?? 0)" unit="组" icon="inst" tone="primary" :spark="spark" desc="柱高为各组机构数" />
      <KpiCard label="被抑制的同级组" :value="String(suppressedN)" unit="组" icon="lock" :tone="suppressedN ? 'warning' : 'success'" :desc="`机构数 < ${minOrgs} 或病例 < ${minCases}`" />
      <KpiCard label="高于同级 P75 的机构" :value="String(flagged.length)" unit="家" icon="alert" :tone="flagged.length ? 'danger' : 'success'" desc="仅未被抑制的组参与判断" />
      <KpiCard label="全市加权均值" :value="res ? String(res.cityMean) : '—'" icon="pct" tone="ai" :desc="`覆盖 ${res?.totalCases ?? 0} 例`" />
    </div>

    <div class="grid grid-cols-[300px_minmax(0,1fr)] items-start gap-3.5">
      <Panel title="试算参数" icon="slider">
        <label class="text-[12px] text-ink-muted">原子指标组合</label>
        <select v-model.number="pick" class="mt-1 w-full rounded-lg border border-line bg-surface px-2.5 py-2 text-[13px]" data-testid="sb-combo">
          <option v-for="(c, i) in combos" :key="i" :value="i">{{ comboName(c) }}</option>
        </select>
        <div class="mt-4 flex items-baseline justify-between text-[12px]"><span class="text-ink-muted">最少机构数(小样本抑制)</span><b class="text-[15px] text-ink tabular-nums" data-testid="sb-minorgs">{{ minOrgs }}</b></div>
        <input v-model.number="minOrgs" type="range" min="1" max="10" step="1" class="mt-1 w-full accent-[var(--c-primary-solid)]" data-testid="sb-minorgs-range" />
        <div class="mt-4 flex items-baseline justify-between text-[12px]"><span class="text-ink-muted">最少病例数</span><b class="text-[15px] text-ink tabular-nums">{{ minCases }}</b></div>
        <input v-model.number="minCases" type="range" min="0" max="2000" step="50" class="mt-1 w-full accent-[var(--c-primary-solid)]" />
        <div class="mt-4 rounded-lg bg-subtle px-3 py-2.5 text-[11px] leading-[1.7] text-ink-muted">
          数字全部由分析引擎试算,本页不写入草稿、不改变已上线指标。被抑制的同级组在机构门户只显示本院值与全市均值,不输出分位。
        </div>
      </Panel>

      <Panel title="同级分布与抑制判定" icon="rpt" :sub="busy ? '试算中…' : '浅蓝带 = P25–P75,竖线 = 中位数,橙点 = 高于 P75'">
        <div v-if="res" class="flex flex-col gap-3">
          <div v-for="g in res.detail" :key="g.name" class="rounded-xl border border-line-soft px-3.5 py-3" :data-sb-group="g.name">
            <div class="mb-2 flex items-center gap-2 text-[12px]">
              <span class="font-semibold text-ink">{{ g.name }}</span>
              <span class="text-ink-muted">{{ g.orgs.length }} 家</span>
              <span v-if="stat(g.name)?.suppressed" class="rounded-full border border-warning-line bg-warning-soft px-2 text-[11px] text-warning-ink" data-sb-suppressed>已抑制 · 不输出分位</span>
              <span v-else class="ml-auto font-mono text-[11px] text-ink-muted tabular-nums">P25 {{ stat(g.name)?.p25 }} · 中位 {{ stat(g.name)?.p50 }} · P75 {{ stat(g.name)?.p75 }}</span>
            </div>
            <div class="relative h-9 rounded-lg bg-subtle">
              <template v-if="!stat(g.name)?.suppressed && stat(g.name)?.p25 != null">
                <div class="absolute inset-y-0 rounded bg-primary-soft" :style="{ left: `${x(stat(g.name)!.p25!)}%`, width: `${x(stat(g.name)!.p75!) - x(stat(g.name)!.p25!)}%` }" />
                <div class="absolute inset-y-1 w-px bg-primary-solid" :style="{ left: `${x(stat(g.name)!.p50!)}%` }" />
              </template>
              <span
                v-for="o in g.orgs"
                :key="o.org"
                class="absolute top-1/2 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface transition-all duration-300"
                :class="!stat(g.name)?.suppressed && stat(g.name)?.p75 != null && o.value > stat(g.name)!.p75! ? 'bg-chart-amber' : 'bg-ink-faint'"
                :style="{ left: `${x(o.value)}%`, background: !stat(g.name)?.suppressed && stat(g.name)?.p75 != null && o.value > stat(g.name)!.p75! ? 'var(--c-chart-amber-dk)' : undefined }"
                :title="`${o.org} · ${o.value} · ${o.cases} 例`"
              />
            </div>
          </div>
        </div>
        <div v-else class="h-48 animate-pulse rounded-lg bg-subtle" />
      </Panel>
    </div>
  </div>
</template>
