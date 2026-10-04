<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fmtValue, portalAnalysisApi, type BenchDetail, type BenchIndex } from '@/api/portalAnalysis'
import type { Tone } from '@/api/types'
import BarTrack from '@/components/portal-analysis/BarTrack.vue'
import PercentileBar from '@/components/portal-analysis/PercentileBar.vue'
import Chip from '@/components/shared/Chip.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/**
 * B3 对标与PK（机构门户）：每个指标按其对标档位（第二批 A13 的 benchmark_tier，审批通过后生效）呈现——
 * 匿名分位 → 分位条；匿名编号 → 横条（他院「三级医院A–E」）；具名对比与排行 → 选择对比医院、两卡并排 + 同级排行。
 * 非具名档后端不下发他院名称；「发起 PK」置灰并提示。
 */
const page = pageDef('B3')!

const idx = ref<BenchIndex | null>(null)
const d = ref<BenchDetail | null>(null)
const sel = ref('')
const pk = ref('')
const error = ref('')

const TIER_TONE: Tone[] = ['muted', 'primary', 'warning']

async function pick(ind: string) {
  sel.value = ind
  try {
    const r = await portalAnalysisApi.benchmark(ind)
    if (sel.value !== ind) return
    d.value = r
    // 具名档默认对比本院之外排名最前的一家
    pk.value = r.named?.rows.find((x) => !x.own)?.name ?? ''
  } catch (e) {
    notifyError(e)
  }
}

async function load() {
  try {
    idx.value = await portalAnalysisApi.benchmarks()
    error.value = ''
    if (idx.value.indicators.length) await pick(sel.value || idx.value.indicators[0].indicator)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}
onMounted(load)

const fmt = (v: number) => (d.value ? fmtValue(v, d.value) : '')
/** 带单位：% 紧贴，元 前置空格。 */
const withUnit = (v: number) => (d.value?.unit === '%' ? `${fmt(v)}%` : d.value?.unit ? `${fmt(v)} ${d.value.unit}` : fmt(v))

/** 横条比例：全为正且差异明显时按「占最大值」；差异很小或含负值时以留白基线拉开差距。 */
function scale(values: number[]) {
  const max = Math.max(...values)
  const min = Math.min(...values)
  if (min > 0 && (max - min) / max > 0.15) return (v: number) => (v / max) * 100
  const lo = min - (max - min || Math.abs(max) || 1) * 0.5
  return (v: number) => ((v - lo) / (max - lo)) * 100
}

const anonRows = computed(() => {
  const rows = d.value?.anonymous ?? []
  const w = scale(rows.map((r) => r.value))
  return rows.map((r) => ({ ...r, w: w(r.value) }))
})

const named = computed(() => d.value?.named)
const shortName = (n: string) => n.replace(/^示例市/, '')
const rankRows = computed(() => {
  const rows = named.value?.rows ?? []
  const w = scale(rows.map((r) => r.value))
  return rows.map((r) => ({ ...r, w: w(r.value) }))
})
const ownRow = computed(() => named.value?.rows.find((r) => r.own))
const pkRow = computed(() => named.value?.rows.find((r) => r.name === pk.value))

function startPk() {
  if (d.value?.tier !== 2) notify('该指标未开放具名对比')
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />

    <div v-if="error" class="mt-5 rounded-[10px] border border-line bg-surface px-6 py-10 text-center text-[13px] text-ink-muted">
      加载失败:{{ error }} <button type="button" class="ml-2 cursor-pointer text-primary" @click="load">重试</button>
    </div>

    <div v-else-if="idx" class="mt-5 grid grid-cols-[280px_minmax(0,1fr)] items-start gap-3.5">
      <!-- 指标与对标档位 -->
      <Panel title="指标与对标档位">
        <div class="flex flex-col gap-1" data-testid="indicators">
          <button
            v-for="r in idx.indicators"
            :key="r.indicator"
            type="button"
            class="flex cursor-pointer items-center justify-between gap-2 rounded-lg border px-2.5 py-[9px] text-left text-[13px] transition-colors"
            :class="r.indicator === sel ? 'border-primary-line-strong bg-primary-tint font-semibold text-primary' : 'border-transparent text-ink hover:bg-hover'"
            :data-indicator="r.indicator"
            :data-tier="r.tier"
            @click="pick(r.indicator)"
          >
            <span>{{ r.indicator }}</span>
            <Tag :tone="TIER_TONE[r.tier]">{{ r.tierName }}</Tag>
          </button>
        </div>
        <div class="mt-3 border-t border-divider pt-2.5 text-[11px] leading-[1.7] text-ink-faint">
          档位由医保局按指标配置,切换须召集人审批;审批通过前按原档位发布。
        </div>
      </Panel>

      <!-- 对标结果 -->
      <section v-if="d" class="min-h-[440px] rounded-[10px] border border-line bg-surface px-[18px] py-4" data-testid="bench-detail" :data-tier="d.tier">
        <div class="mb-4 flex items-start justify-between gap-4">
          <div>
            <div class="text-[15px] font-semibold text-ink" data-testid="bench-title">{{ d.indicator }}</div>
            <div class="text-[12px] text-ink-muted">
              {{ d.peerGroup }}同级组 {{ d.peerCount }} 家 · {{ d.periodLabel }} · 档位:<span data-testid="bench-tier">{{ d.tierName }}</span>
            </div>
          </div>
          <span v-if="d.tier === 2" class="inline-flex h-[30px] items-center rounded-lg bg-primary-solid px-3.5 text-[12px] text-white" data-testid="pk-open">具名对比已开放</span>
          <button
            v-else
            type="button"
            aria-disabled="true"
            title="该指标未开放具名对比"
            class="inline-flex h-[30px] cursor-not-allowed items-center rounded-lg bg-chip px-3.5 text-[12px] text-ink-ghost"
            data-testid="pk-disabled"
            @click="startPk"
          >
            具名对比未开放
          </button>
        </div>

        <!-- 匿名分位 -->
        <template v-if="d.tier === 0 && d.percentile">
          <div v-if="d.percentile.suppressed" class="rounded-lg border border-line bg-subtle px-4 py-6 text-center text-[12px] text-ink-muted" data-testid="pct-suppressed">
            本院 {{ withUnit(d.percentile.ownValue) }} · {{ d.percentile.message }}
          </div>
          <template v-else>
            <div class="mb-5 flex gap-8">
              <div>
                <div class="text-[11px] text-ink-muted">本院</div>
                <div class="text-[26px] font-semibold text-ink" data-testid="own-value">{{ withUnit(d.percentile.ownValue) }}</div>
              </div>
              <div>
                <div class="text-[11px] text-ink-muted">同级 P25 / P50 / P75</div>
                <div class="mt-2 text-[16px] text-ink" data-testid="quartiles">
                  {{ fmt(d.percentile.p25!) }} / {{ fmt(d.percentile.p50!) }} / {{ withUnit(d.percentile.p75!) }}
                </div>
              </div>
            </div>
            <PercentileBar :pct="d.percentile.ownPct!" :concern="d.percentile.concern" />
            <div class="mt-3.5 text-[12px] text-ink-sub">
              本院位于同级 <b :class="d.percentile.concern ? 'text-warning' : 'text-primary'" data-testid="own-pct">P{{ d.percentile.ownPct }}</b>{{ d.percentile.concern ? '(高为差,已标橙关注)' : '' }}。匿名分位档位下,不显示其他医院名称、编号与数值。
            </div>
          </template>
        </template>

        <!-- 匿名编号 -->
        <template v-else-if="d.tier === 1">
          <div data-testid="anon-rows">
            <div v-for="a in anonRows" :key="a.label" class="grid grid-cols-[96px_1fr_64px] items-center gap-2.5 py-1.5 text-[12px]" :data-own="a.own">
              <span :class="a.own ? 'font-semibold text-primary' : 'text-ink'">{{ a.label }}</span>
              <BarTrack :width="a.w" :height="14" :color="a.own ? 'var(--c-primary-solid)' : 'var(--c-bar-neutral-a)'" />
              <span class="text-right" :class="a.own ? 'font-semibold text-ink' : 'text-ink-sub'">{{ withUnit(a.value) }}</span>
            </div>
          </div>
          <div class="mt-3 text-[12px] text-ink-sub">匿名编号档位:他院以“三级医院A–E”呈现,按数值顺序编号,每期重排,不与具体医院对应。</div>
        </template>

        <!-- 具名对比与排行 -->
        <template v-else-if="d.tier === 2 && named">
          <div class="mb-3.5 flex flex-wrap items-center gap-1.5" data-testid="pk-chips">
            <span class="mr-1 text-[12px] text-ink-muted">选择对比医院</span>
            <Chip v-for="r in named.rows.filter((x) => !x.own)" :key="r.name" :on="r.name === pk" :data-pk="r.name" @click="pk = r.name">{{ shortName(r.name) }}</Chip>
          </div>
          <div class="grid grid-cols-2 gap-3" data-testid="pk-cards">
            <div v-if="ownRow" class="rounded-[10px] border-[1.5px] border-primary px-4 py-3.5">
              <div class="text-[12px] text-ink-muted">本院</div>
              <div class="font-semibold text-ink">{{ ownRow.name }}</div>
              <div class="mt-1.5 text-[28px] font-semibold text-primary">{{ withUnit(ownRow.value) }}</div>
              <div class="text-[12px] text-ink-sub">同级第 {{ ownRow.rank }} 名</div>
            </div>
            <div v-if="pkRow" class="rounded-[10px] border border-line px-4 py-3.5" data-testid="pk-card">
              <div class="text-[12px] text-ink-muted">对比医院</div>
              <div class="font-semibold text-ink">{{ pkRow.name }}</div>
              <div class="mt-1.5 text-[28px] font-semibold text-ink">{{ withUnit(pkRow.value) }}</div>
              <div class="text-[12px] text-ink-sub">同级第 {{ pkRow.rank }} 名</div>
            </div>
          </div>
          <div class="mt-4 mb-1.5 text-[12px] font-semibold text-ink">同级排行</div>
          <div data-testid="rank-rows">
            <div v-for="a in rankRows" :key="a.name" class="grid grid-cols-[20px_160px_1fr_64px] items-center gap-2.5 py-[5px] text-[12px]">
              <span class="text-ink-faint">{{ a.rank }}</span>
              <span :class="a.own ? 'font-semibold text-primary' : a.name === pk ? 'font-semibold text-ink' : 'text-ink'">{{ a.name }}</span>
              <BarTrack :width="a.w" :color="a.own ? 'var(--c-primary-solid)' : a.name === pk ? 'var(--c-chart-amber-dk)' : 'var(--c-bar-neutral-a)'" />
              <span class="text-right text-ink">{{ withUnit(a.value) }}</span>
            </div>
          </div>
        </template>

        <div class="mt-5 border-t border-divider pt-2.5 text-[11px] text-ink-faint">口径:{{ d.note }}{{ d.higherIsBetter ? ' · 越高越好' : ' · 越低越好' }}</div>
      </section>
      <div v-else class="h-[440px] animate-pulse rounded-[10px] border border-line bg-surface" />
    </div>
    <div v-else class="mt-5 h-[440px] animate-pulse rounded-[10px] border border-line bg-surface" />
  </div>
</template>
