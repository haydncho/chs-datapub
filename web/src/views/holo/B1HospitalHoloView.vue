<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fmt, holoApi, signed } from '@/api/holo'
import type { HospitalHolo } from '@/api/holo'
import BubbleChart from '@/components/holo/BubbleChart.vue'
import type { ChartBubble } from '@/components/holo/BubbleChart.vue'
import PercentileBar from '@/components/holo/PercentileBar.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import { pageDef } from '@/lib/nav'

/**
 * B1 本院全息图（机构门户，发布区）：本院病组为彩色气泡（红逆差 / 绿结余，|差额| < 150 元降透明度），
 * 全市同级同组均值为灰色背景；六项核心指标只显示同级匿名分位；医保记账 vs DRG 支付标准。
 * 后端只下发本院数据与同级均值（无他院任何字段）；本院病例 < 30 的病组已并入“其他”。
 */
const page = pageDef('B1')!
const router = useRouter()
const OTHER = '__other__'

const h = ref<HospitalHolo | null>(null)
const error = ref('')
const sel = ref('BR25')

async function load() {
  try {
    h.value = await holoApi.hospital()
    error.value = ''
    if (h.value && !h.value.own.some((o) => o.code === sel.value)) sel.value = h.value.own[0]?.code ?? OTHER
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}
onMounted(load)

const dirColor = (v: number) => (v > 0 ? 'var(--c-red-solid)' : 'var(--c-success-solid)')
const bubbles = computed<ChartBubble[]>(() => {
  if (!h.value) return []
  const own: ChartBubble[] = h.value.own.map((o) => ({
    id: o.code,
    cases: o.cases,
    diff: o.avgDiff,
    r: o.radius,
    fill: dirColor(o.avgDiff),
    opacity: o.faded ? 0.55 : 0.9,
    tip: `${o.code} ${o.name} · 本院 ${fmt(o.cases)} 例 · 例均差额 ${signed(o.avgDiff)} 元`,
  }))
  const m = h.value.merged
  if (m) {
    own.push({
      id: OTHER,
      cases: m.cases,
      diff: m.avgDiff,
      r: 7,
      fill: 'var(--c-text-faint)',
      opacity: 0.6,
      dashed: true,
      label: '其他',
      tip: `其他(已并入 ${m.count} 个本院病例 < 30 的病组)· 合计 ${fmt(m.cases)} 例 · 例均差额 ${signed(m.avgDiff)} 元`,
    })
  }
  return own
})
const background = computed(() =>
  (h.value?.peers ?? []).map((p) => ({ cases: p.cases, diff: p.avgDiff, r: p.radius, tip: `${p.code} 全市同级同组均值 · ${fmt(p.cases)} 例 · ${signed(p.avgDiff)} 元` })),
)
const cur = computed(() => h.value?.own.find((o) => o.code === sel.value) ?? null)

function goB2(code: string) {
  void router.push({ name: 'B2', query: { code } })
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />

    <div v-if="error" class="mt-5 rounded-[10px] border border-line bg-surface px-6 py-10 text-center text-[13px] text-ink-muted">
      本院全息图加载失败:{{ error }} <button type="button" class="ml-2 cursor-pointer text-primary" @click="load">重试</button>
    </div>

    <template v-else-if="h">
      <!-- 机构头 -->
      <div class="mt-5 flex flex-wrap items-center gap-4 rounded-[10px] border border-line bg-surface px-[18px] py-3" data-testid="org-head">
        <div>
          <div class="text-[16px] font-semibold text-ink" data-testid="org-name">{{ h.org.name }}</div>
          <div class="text-[12px] text-ink-muted">同级组:{{ h.org.tier }}({{ h.org.peerCount }} 家)· {{ h.org.period }} · 对标档位:{{ h.org.tierMode }}</div>
        </div>
        <div class="flex-1" />
        <div class="flex gap-2 text-[12px]">
          <span v-if="h.org.pendingSign" class="rounded-md bg-warning-soft px-2.5 py-1 text-warning-ink">{{ h.org.pendingSign }}</span>
          <span v-if="h.org.alert" class="rounded-md bg-danger-soft px-2.5 py-1 text-danger-ink">{{ h.org.alert }}</span>
        </div>
      </div>

      <div class="mt-3 grid grid-cols-[minmax(0,1fr)_380px] items-start gap-3">
        <!-- 本院病组全景 -->
        <section class="rounded-[10px] border border-line bg-surface px-4 py-3.5">
          <div class="sect-title">本院病组全景</div>
          <div class="text-[12px] text-ink-muted">彩色为本院病组(红=逆差,绿=结余),灰色为全市同级机构同组均值 · 横轴本院病例数 · 纵轴例均基金差额</div>
          <div class="mt-1.5" data-testid="bubble-chart">
            <BubbleChart :axis="h.axis" :bubbles="bubbles" :background="background" :selected="sel" :height="400" x-title="本院病例数(例)" @select="sel = $event" />
          </div>
          <div class="mt-1 flex flex-wrap items-center gap-x-3.5 gap-y-1 text-[11px] text-ink-sub">
            <span class="flex items-center gap-1"><span class="size-2.5 rounded-full" style="background: var(--c-red-solid)" />本院逆差</span>
            <span class="flex items-center gap-1"><span class="size-2.5 rounded-full" style="background: var(--c-success-solid)" />本院结余</span>
            <span class="flex items-center gap-1"><span class="size-2.5 rounded-full opacity-55" style="background: var(--c-red-solid)" />|差额| &lt; 150 元(浅色)</span>
            <span class="flex items-center gap-1"><span class="size-2.5 rounded-full opacity-50" style="background: var(--c-text-ghost)" />全市同级同组均值</span>
            <span v-if="h.merged" class="flex items-center gap-1"><span class="size-2.5 rounded-full border border-dashed border-ink-muted" />其他(病例 &lt; 30 并入)</span>
          </div>
          <!-- 选中病组信息条 -->
          <div class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg bg-subtle px-3 py-2.5 text-[12px]" data-testid="sel-strip">
            <template v-if="cur">
              <span class="font-mono font-semibold text-primary">{{ cur.code }}</span>
              <span class="font-medium text-ink">{{ cur.name }}</span>
              <span class="text-ink tabular-nums">本院 {{ fmt(cur.cases) }} 例</span>
              <span class="text-ink">例均差额 <b class="tabular-nums" :class="cur.avgDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(cur.avgDiff) }} 元</b></span>
              <span v-if="cur.peerDiff !== undefined && cur.peerDiff !== null" class="text-ink-sub">全市同组 <span class="tabular-nums" :class="cur.peerDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(cur.peerDiff) }} 元</span></span>
              <span class="flex-1" />
              <button type="button" class="cursor-pointer text-primary" data-testid="go-b2" @click="goB2(cur.code)">病组下钻 ›</button>
            </template>
            <template v-else-if="h.merged">
              <span class="font-semibold text-ink">其他</span>
              <span class="text-ink tabular-nums">本院 {{ fmt(h.merged.cases) }} 例 · {{ h.merged.count }} 个病组</span>
              <span class="text-ink">例均差额 <b class="tabular-nums" :class="h.merged.avgDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(h.merged.avgDiff) }} 元</b></span>
              <span class="flex-1" />
              <span class="text-warning-ink" data-testid="merged-note">本院病例 &lt; 30,机构门户中已并入“其他”</span>
            </template>
          </div>
        </section>

        <div class="flex flex-col gap-3">
          <!-- 六项核心指标 -->
          <Panel title="核心指标 · 同级分位" data-testid="percentiles">
            <template #head><span class="text-[11px] text-ink-muted">{{ h.org.tier }} {{ h.org.peerCount }} 家 · 匿名</span></template>
            <div v-if="h.indicators.suppressed" class="rounded-lg bg-subtle px-3 py-4 text-center text-[12px] text-ink-muted">{{ h.indicators.message }}</div>
            <div v-else class="flex flex-col gap-3.5">
              <div v-for="p in h.indicators.rows" :key="p.name" :data-indicator="p.name">
                <div class="mb-1 flex items-baseline justify-between text-[12px]">
                  <span class="text-ink">{{ p.name }}</span>
                  <span class="text-ink"><b class="text-[14px] tabular-nums">{{ p.value }}</b> {{ p.unit }}
                    <span class="ml-1.5 font-semibold" :class="p.tone === 'warning' ? 'text-warning' : 'text-primary'">P{{ p.pct }}</span></span>
                </div>
                <PercentileBar :pct="p.pct" :tone="p.tone" />
                <div v-if="p.note" class="mt-0.5 text-[11px] text-warning">{{ p.note }}</div>
              </div>
            </div>
            <div class="mt-2 text-[11px] text-ink-faint">浅蓝带为同级 P25–P75,竖线为中位数</div>
          </Panel>

          <!-- 医保记账 vs DRG 支付标准 -->
          <Panel title="医保记账 vs DRG 支付标准" data-testid="ledger">
            <div class="grid grid-cols-2 gap-2">
              <div class="rounded-lg bg-subtle px-2.5 py-2"><div class="text-[11px] text-ink-muted">医保记账总额</div><div class="text-[16px] font-semibold text-ink tabular-nums">{{ h.ledger.ledgerWan.toLocaleString('zh-CN', { minimumFractionDigits: 1 }) }} 万</div></div>
              <div class="rounded-lg bg-subtle px-2.5 py-2"><div class="text-[11px] text-ink-muted">DRG 支付总额</div><div class="text-[16px] font-semibold text-ink tabular-nums">{{ h.ledger.drgPayWan.toLocaleString('zh-CN', { minimumFractionDigits: 1 }) }} 万</div></div>
              <div class="rounded-lg px-2.5 py-2" :class="h.ledger.direction === '逆差' ? 'bg-danger-soft' : 'bg-success-soft'">
                <div class="text-[11px] text-ink-muted">偏离</div>
                <div class="text-[16px] font-semibold tabular-nums" :class="h.ledger.direction === '逆差' ? 'text-danger' : 'text-success'" data-testid="ledger-dev">{{ h.ledger.direction }} {{ h.ledger.deviationWan.toFixed(1) }} 万</div>
                <div class="text-[11px] tabular-nums" :class="h.ledger.direction === '逆差' ? 'text-danger-ink' : 'text-success-ink'">{{ h.ledger.deviationPct > 0 ? '+' : '−' }}{{ Math.abs(h.ledger.deviationPct).toFixed(1) }}%</div>
              </div>
              <div class="rounded-lg px-2.5 py-2" :class="h.ledger.avgDiff > 0 ? 'bg-danger-soft' : 'bg-success-soft'">
                <div class="text-[11px] text-ink-muted">例均基金差额</div>
                <div class="text-[16px] font-semibold tabular-nums" :class="h.ledger.avgDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(h.ledger.avgDiff) }} 元</div>
                <div class="text-[11px]" :class="h.ledger.avgDiff > 0 ? 'text-danger-ink' : 'text-success-ink'">同级 P{{ h.ledger.diffPct }}</div>
              </div>
            </div>
            <div class="mt-3 mb-1.5 text-[12px] text-ink-sub">例均差额近 6 月</div>
            <div class="flex h-[84px] items-end gap-2.5" data-testid="trend">
              <div v-for="t in h.trend" :key="t.month" class="flex h-full flex-1 flex-col items-center justify-end gap-0.5">
                <span class="text-[10px] tabular-nums" :class="t.avgDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(t.avgDiff) }}</span>
                <div class="w-full rounded-t-[2px]" :style="{ height: `${t.heightPct * 0.56}px`, background: t.avgDiff > 0 ? 'var(--c-s-red-b)' : 'var(--c-s-green-b)' }" />
                <span class="text-[10px] text-ink-muted">{{ t.month }}</span>
              </div>
            </div>
            <div class="mt-3 mb-1 text-[12px] text-ink-sub">逆差贡献最大的病组</div>
            <button
              v-for="t in h.top"
              :key="t.code"
              type="button"
              class="grid w-full cursor-pointer grid-cols-[50px_1fr_50px_70px] border-b border-divider py-[5px] text-left text-[12px] hover:bg-hover"
              :data-top="t.code"
              @click="sel = t.code"
            >
              <span class="font-mono text-primary">{{ t.code }}</span><span class="text-ink">{{ t.shortName }}</span>
              <span class="text-right text-ink-sub tabular-nums">{{ t.cases }}例</span><span class="text-right tabular-nums" :class="t.avgDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(t.avgDiff) }}</span>
            </button>
          </Panel>
        </div>
      </div>
    </template>
    <div v-else class="mt-5 h-[520px] animate-pulse rounded-[10px] border border-line bg-surface" />
  </div>
</template>
