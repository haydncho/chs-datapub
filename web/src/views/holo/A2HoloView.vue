<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BAND_COLOR, fmt, holoApi, signed, wanText } from '@/api/holo'
import type { GroupDetail, HoloOverview, HoloPeriod, Offsite } from '@/api/holo'
import BubbleChart from '@/components/holo/BubbleChart.vue'
import type { ChartBubble } from '@/components/holo/BubbleChart.vue'
import FlowMap from '@/components/holo/FlowMap.vue'
import PercentileBar from '@/components/holo/PercentileBar.vue'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import SegTabs from '@/components/shared/SegTabs.vue'
import Tag from '@/components/shared/Tag.vue'
import { pageDef } from '@/lib/nav'
import { notifyError } from '@/lib/notify'

/**
 * A2 医保数据公开全息图（召集人 / 行政管理组，分析监测区）：一屏掌握“钱、效、错”与公开状态，定位关键少数病组并下钻。
 * 状态：drill（下钻层级）、period（月 | 季 | 年）、layer（图层）、sel（选中病组）。派生数字均来自引擎。
 */
const page = pageDef('A2')!
const router = useRouter()

type Layer = 'money' | 'eff' | 'err' | 'out' | 'pub'
const LAYERS: { id: Layer; name: string; desc: string; dot: string }[] = [
  { id: 'money', name: '钱 · 基金与结算', desc: '基金支出 · 例均差额 · 结算偏离', dot: 'var(--c-primary-solid)' },
  { id: 'eff', name: '效 · 支付与效率', desc: 'CMI · 费用/时间消耗指数', dot: 'var(--c-chart-blue)' },
  { id: 'err', name: '错 · 审核与监管', desc: '审核扣款 · 疑似高套 · 编码质量', dot: 'var(--c-red-solid)' },
  { id: 'out', name: '区域外 · 异地就医', desc: '流向城市 · 基金支出占比', dot: 'var(--c-chart-amber-dk)' },
  { id: 'pub', name: '公开状态', desc: '公开目录 × 受众 · 问题定位', dot: 'var(--c-success-solid)' },
]
const TITLES: Record<Layer, [string, string]> = {
  money: ['病组气泡全景 · 基金与结算', '横轴病例数 · 纵轴例均基金差额(零线为收支平衡)· 圆大小为次均总费用 · 颜色为差额总额'],
  eff: ['病组气泡全景 · 叠加支付效率', '橙色外环:时间消耗指数 > 1.10 的病组'],
  err: ['病组气泡全景 · 叠加审核监管', '红色外环:本期审核扣款或疑似高套集中的病组'],
  out: ['异地就医流向 · 区域外', '按地区汇总'],
  pub: ['公开目录矩阵 · 指标 × 受众', '状态色标出三类问题:该公开未公开 / 发了没人看 / 意见集中未答复'],
}
const PERIODS: { value: HoloPeriod; label: string }[] = [
  { value: '月', label: '月' },
  { value: '季', label: '季' },
  { value: '年', label: '年' },
]
const MIX_COLORS = ['var(--c-primary-solid)', 'var(--c-chart-blue)', 'var(--c-s-blue-b)', 'var(--c-bar-neutral-b)', 'var(--c-bar-light-b)']
const LEVEL_COLORS = ['var(--c-primary-solid)', 'var(--c-s-blue-b)', 'var(--c-bar-light-b)']
const CELL: Record<string, string> = {
  ok: 'bg-success-soft text-success-ink',
  low: 'bg-warning-soft text-warning-ink',
  np: 'bg-danger-soft text-danger-ink',
  cmt: 'bg-ai-soft text-ai-ink',
  int: 'bg-chip text-ink-ghost',
  na: 'border border-divider bg-surface text-ink-ghost',
}
const PEER_COLOR = { danger: 'text-danger', warning: 'text-warning', primary: 'text-primary' }
const RING = { eff: 'var(--c-chart-amber-dk)', err: 'var(--c-red-solid)' }

const layer = ref<Layer>('money')
const period = ref<HoloPeriod>('月')
const drill = ref(0)
const sel = ref('BR25')
const focusFreq = ref(false)

const ov = ref<HoloOverview | null>(null)
const det = ref<GroupDetail | null>(null)
const off = ref<Offsite | null>(null)
const error = ref('')

async function load() {
  try {
    ov.value = await holoApi.overview(period.value)
    error.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}
async function loadDetail() {
  try {
    det.value = await holoApi.group(sel.value, period.value)
  } catch (e) {
    notifyError(e)
  }
}
async function loadOffsite() {
  if (off.value) return
  try {
    off.value = await holoApi.offsite()
  } catch (e) {
    notifyError(e)
  }
}
onMounted(() => {
  void load()
  void loadDetail()
})
watch(period, () => {
  void load()
  void loadDetail()
})
watch(sel, () => void loadDetail())
watch(layer, (l) => {
  if (l === 'out') void loadOffsite()
})

const pano = computed(() => ov.value?.panorama)
const top20 = computed(() => new Set([...(pano.value?.groups ?? [])].sort((a, b) => b.cases - a.cases).slice(0, 20).map((g) => g.code)))
const bubbles = computed<ChartBubble[]>(() =>
  (pano.value?.groups ?? []).map((g) => ({
    id: g.code,
    cases: g.cases,
    diff: g.avgDiff,
    r: g.radius,
    fill: BAND_COLOR[g.band],
    opacity: focusFreq.value && !top20.value.has(g.code) ? 0.25 : 0.9,
    ring: layer.value === 'eff' && g.effFlag ? RING.eff : layer.value === 'err' && g.errFlag ? RING.err : null,
    label: g.isKey ? `${g.code} ${g.shortName}` : undefined,
    tip: `${g.code} ${g.name} · 病例 ${fmt(g.cases)} · 例均差额 ${signed(g.avgDiff)} 元 · 次均 ${fmt(g.avgCost)} 元`,
  })),
)
const ringCount = computed(() => (pano.value?.groups ?? []).filter((g) => (layer.value === 'eff' ? g.effFlag : g.errFlag)).length)

const drillLevels = computed(() => {
  const topBeh = det.value ? [...det.value.behaviors].sort((a, b) => b.multiplier - a.multiplier)[0]?.name : undefined
  return [
    ['统筹区', '示例市'],
    ['县区', '全部'],
    ['机构', '全部'],
    ['病组', drill.value >= 3 ? sel.value : '—'],
    ['诊疗行为', drill.value >= 4 ? (topBeh ?? '—') : '—'],
  ] as const
})

function select(code: string) {
  sel.value = code
  drill.value = Math.max(drill.value, 3)
}

function shortcut(kind: string) {
  if (kind === 'key') {
    layer.value = 'money'
    const k = [...(pano.value?.groups ?? [])].filter((g) => g.isKey && g.avgDiff > 0).sort((a, b) => b.totalDiff - a.totalDiff)[0]
    if (k) select(k.code)
  } else if (kind === 'freq') {
    layer.value = 'money'
    focusFreq.value = !focusFreq.value
  } else if (kind === 'hot') {
    void router.push({ name: 'A10' })
  } else {
    void router.push({ name: 'A8' })
  }
}

// ---------------------------------------------------------------- 驾驶舱:KPI、巡航、全屏、播报
const root = ref<HTMLElement | null>(null)
const fullscreen = ref(false)
const cruise = ref(false)
let cruiseTimer = 0
const now = ref(new Date())
let clock = 0
const hhmmss = computed(() => now.value.toLocaleTimeString('zh-CN', { hour12: false }))

const groups = computed(() => pano.value?.groups ?? [])
const totalCases = computed(() => groups.value.reduce((n, g) => n + g.cases, 0))
const keyGroups = computed(() => [...groups.value].filter((g) => g.isKey).sort((a, b) => b.totalDiff - a.totalDiff))
const bandSpark = computed(() => (pano.value?.bands ?? []).map((b) => groups.value.filter((g) => g.band === b.key).length))
const keySpark = computed(() => keyGroups.value.slice(0, 10).map((g) => Math.abs(g.totalDiff)))
const caseSpark = computed(() => [...groups.value].sort((a, b) => b.cases - a.cases).slice(0, 12).map((g) => g.cases))
const status = computed(() => ov.value?.publication.status)
const ticker = computed(() => {
  const out: string[] = []
  const k = keyGroups.value[0]
  if (k) out.push(`逆差最大病组 ${k.code} ${k.shortName}:例均差额 ${signed(k.avgDiff)} 元,病例 ${fmt(k.cases)} 例`)
  if (pano.value) out.push(`关键少数病组 ${pano.value.keyCount} 个,合计逆差 ${wanText(pano.value.keyDeficitWan)}`)
  const c = ov.value?.publication.counts
  if (c) out.push(`公开状态:${c.np} 项该公开未公开 · ${c.low} 项发了没人看 · ${c.cmt} 项意见集中未答复`)
  if (status.value) out.push(`最近期次 ${status.value.latest} · 签收率 ${status.value.signPct}% · 查阅率 ${status.value.readPct}% · 答复率 ${status.value.replyPct}%(${status.value.overdue} 条超期)`)
  return out
})

function startCruise() {
  window.clearInterval(cruiseTimer)
  if (!cruise.value) return
  let i = Math.max(0, keyGroups.value.findIndex((g) => g.code === sel.value))
  cruiseTimer = window.setInterval(() => {
    if (!keyGroups.value.length) return
    i = (i + 1) % keyGroups.value.length
    select(keyGroups.value[i].code)
  }, 6000)
}
watch(cruise, startCruise)
async function toggleFullscreen() {
  if (document.fullscreenElement) await document.exitFullscreen()
  else await root.value?.requestFullscreen?.()
}
const onFs = () => (fullscreen.value = !!document.fullscreenElement)
onMounted(() => {
  document.addEventListener('fullscreenchange', onFs)
  clock = window.setInterval(() => (now.value = new Date()), 1000)
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFs)
  window.clearInterval(cruiseTimer)
  window.clearInterval(clock)
})

const mainSub = computed(() => (layer.value === 'out' && off.value ? `按地区汇总 · 来源:${off.value.source}` : TITLES[layer.value][1]))
const isBubbleLayer = computed(() => layer.value === 'money' || layer.value === 'eff' || layer.value === 'err')
</script>

<template>
  <div ref="root" class="cockpit ck-root min-h-[calc(100dvh-var(--shell-top,0px))] px-8 pt-[22px] pb-4" data-testid="cockpit" :data-fullscreen="fullscreen">
    <PageHeader :page="page" title="医保数据公开全息图">
      <template #actions>
        <span class="mr-1 hidden items-center gap-2 text-[12px] text-ink-sub xl:flex"><i class="ck-live" />实时监测 <b class="font-mono text-ink tabular-nums" data-testid="ck-clock">{{ hhmmss }}</b></span>
        <button
          type="button"
          class="cursor-pointer rounded-lg border px-3 py-[7px] text-[12px] transition-colors"
          :class="cruise ? 'border-primary bg-primary-tint text-primary' : 'border-line bg-surface text-ink-sub hover:bg-hover'"
          :aria-pressed="cruise"
          data-testid="cruise-btn"
          @click="cruise = !cruise"
        >{{ cruise ? '■ 停止巡航' : '▶ 关键病组巡航' }}</button>
        <button type="button" class="cursor-pointer rounded-lg border border-line bg-surface px-3 py-[7px] text-[12px] text-ink-sub transition-colors hover:bg-hover" data-testid="fullscreen-btn" @click="toggleFullscreen">
          {{ fullscreen ? '退出全屏' : '大屏模式' }}
        </button>
      </template>
    </PageHeader>

    <div v-if="error" class="mt-5 rounded-[10px] border border-line bg-surface px-6 py-10 text-center text-[13px] text-ink-muted">
      全息图加载失败:{{ error }} <button type="button" class="ml-2 cursor-pointer text-primary" @click="load">重试</button>
    </div>

    <template v-else>
      <div class="mt-5 grid grid-cols-6 gap-3" data-testid="ck-kpis">
        <KpiCard class="ck-rise" label="监测病组" :value="String(groups.length)" unit="个" icon="holo" tone="primary" :spark="bandSpark" animate :desc="ov?.periodLabel ?? ''" />
        <KpiCard class="ck-rise" style="animation-delay: 60ms" label="关键少数病组" :value="String(pano?.keyCount ?? 0)" unit="个" icon="alert" tone="danger" :spark="keySpark" animate desc="逆差集中,优先下钻" />
        <KpiCard class="ck-rise" style="animation-delay: 120ms" label="关键少数合计逆差" :value="pano ? wanText(pano.keyDeficitWan) : '—'" icon="money" tone="warning" :spark="keySpark" spark-type="line" animate small desc="按差额总额汇总" />
        <KpiCard class="ck-rise" style="animation-delay: 180ms" label="结算病例" :value="fmt(totalCases)" unit="例" icon="bed" tone="ai" :spark="caseSpark" animate desc="全部监测病组合计" />
        <KpiCard class="ck-rise" style="animation-delay: 240ms" label="发布签收率" :value="status ? `${status.signPct}%` : '—'" icon="send" tone="success" animate :desc="status ? `查阅率 ${status.readPct}%` : ''" />
        <KpiCard class="ck-rise" style="animation-delay: 300ms" label="意见答复率" :value="status ? `${status.replyPct}%` : '—'" icon="opinion" :tone="status && status.replyPct < 60 ? 'danger' : 'primary'" animate :desc="status ? `${status.opinions} 条意见 · ${status.overdue} 条超期` : ''" />
      </div>

      <!-- 层级下钻 + 口径 -->
      <div class="mt-5 flex items-center gap-3 ck-card px-3 py-2" data-testid="drill-bar">
        <span class="text-[12px] whitespace-nowrap text-ink-faint">层级下钻</span>
        <div class="flex flex-1 flex-wrap items-center gap-1">
          <template v-for="([lvl, val], i) in drillLevels" :key="lvl">
            <button
              type="button"
              class="flex cursor-pointer items-baseline gap-1.5 rounded-md px-2.5 py-1"
              :class="i === drill ? 'bg-primary-solid text-white' : 'bg-chip text-ink hover:bg-hover'"
              :aria-current="i === drill ? 'step' : undefined"
              :data-drill="lvl"
              @click="drill = i"
            >
              <span class="text-[11px]" :class="i === drill ? 'text-white/80' : 'text-ink-muted'">{{ lvl }}</span>
              <span class="font-medium">{{ val }}</span>
            </button>
            <span v-if="i < 4" class="text-ink-ghost">›</span>
          </template>
        </div>
        <SegTabs v-model="period" :items="PERIODS" size="sm" data-testid="period-tabs" />
        <span class="min-w-[104px] text-[12px] whitespace-nowrap text-ink-sub" data-testid="period-label">{{ ov?.periodLabel ?? '' }}</span>
      </div>

      <div class="mt-3 grid grid-cols-[196px_minmax(0,1fr)_336px] items-start gap-3">
        <!-- 图层 -->
        <nav class="ck-card flex flex-col gap-1.5 px-2.5 py-3" data-testid="layers">
          <div class="px-1 pb-1 text-[13px] font-semibold text-ink">图层</div>
          <button
            v-for="l in LAYERS"
            :key="l.id"
            type="button"
            class="cursor-pointer rounded-lg border px-2.5 py-2 text-left transition-colors"
            :class="l.id === layer ? 'border-primary bg-primary-tint' : 'border-line bg-surface hover:bg-hover'"
            :aria-pressed="l.id === layer"
            :data-layer="l.id"
            @click="layer = l.id"
          >
            <div class="flex items-center gap-2">
              <span class="size-2 flex-none rounded-[2px]" :style="{ background: l.dot }" />
              <span class="text-[13px] font-semibold" :class="l.id === layer ? 'text-primary' : 'text-ink'">{{ l.name }}</span>
            </div>
            <div class="mt-0.5 pl-4 text-[11px] text-ink-muted">{{ l.desc }}</div>
          </button>
          <div class="mt-1.5 border-t border-divider px-1.5 pt-2 text-[11px] leading-[1.6] text-ink-muted">
            当前为召集人视角:分析监测区数据,可下钻至诊疗行为。机构仅可见经审批的聚合结果。
          </div>
        </nav>

        <!-- 主视图 -->
        <div class="flex min-w-0 flex-col gap-3">
          <section class="ck-card px-4 py-3.5" data-testid="main-view" :data-layer="layer">
            <div class="sect-title" data-testid="main-title">{{ TITLES[layer][0] }}</div>
            <div class="mt-0.5 text-[12px] text-ink-muted">{{ mainSub }}</div>

            <!-- 气泡全景（钱 / 效 / 错） -->
            <template v-if="isBubbleLayer">
              <div v-if="pano" class="mt-1.5" data-testid="bubble-chart">
                <BubbleChart :axis="pano.axis" :bubbles="bubbles" :selected="sel" :height="430" x-title="病例数(例)" animate @select="select" />
              </div>
              <div v-else class="mt-1.5 h-[430px] animate-pulse rounded-lg bg-subtle" />
              <div v-if="pano" class="mt-2.5 flex flex-wrap items-center gap-x-3.5 gap-y-1.5 border-t border-divider pt-2.5 text-[11px] text-ink-sub">
                <span>差额总额</span>
                <span v-for="b in pano.bands" :key="b.key" class="flex items-center gap-1">
                  <span class="size-2.5 rounded-full" :style="{ background: BAND_COLOR[b.key] }" />{{ b.label }}
                </span>
                <span v-if="layer === 'eff' || layer === 'err'" class="flex items-center gap-1" data-testid="ring-legend">
                  <span class="size-3 rounded-full border-2" :style="{ borderColor: RING[layer] }" />
                  {{ layer === 'eff' ? '时间消耗指数 > 1.10' : '审核扣款 / 疑似高套' }} {{ ringCount }} 个
                </span>
                <span class="flex-1" />
                <span class="text-ink" data-testid="key-summary">
                  关键少数病组 <b>{{ pano.keyCount }}</b> 个 · 合计逆差 <b class="text-danger">{{ wanText(pano.keyDeficitWan) }}</b>
                </span>
              </div>
            </template>

            <!-- 区域外 -->
            <template v-else-if="layer === 'out'">
              <div v-if="off" class="mt-2.5 flex flex-col gap-3.5">
                <FlowMap :flows="off.flows" animate />
                <div class="grid grid-cols-3 gap-3.5 border-t border-divider pt-3 text-[12px]">
                  <div class="rounded-lg bg-subtle px-3 py-2.5">
                    <div class="text-[11px] text-ink-muted">异地就医基金支出</div>
                    <div class="text-[20px] font-semibold text-ink tabular-nums">{{ off.totalText }}</div>
                    <div class="text-ink-sub">占全市统筹基金支出 {{ off.fundSharePct }}% · {{ fmt(off.visits) }} 人次</div>
                  </div>
                  <div>
                    <div class="mb-1 text-[11px] text-ink-muted">按就医地机构等级</div>
                    <div class="flex h-2.5 overflow-hidden rounded-[3px]">
                      <span v-for="(l, i) in off.levels" :key="l.name" :style="{ width: `${l.pct}%`, background: LEVEL_COLORS[i] }" />
                    </div>
                    <div class="mt-1 flex gap-2.5 text-[11px] text-ink-sub"><span v-for="l in off.levels" :key="l.name">{{ l.name }} {{ l.pct }}%</span></div>
                  </div>
                  <div>
                    <div class="mb-1 text-[11px] text-ink-muted">外流病种 Top 4(按基金)</div>
                    <div v-for="d in off.diseases" :key="d.name" class="flex justify-between text-ink"><span>{{ d.name }}</span><span class="tabular-nums">{{ d.pct }}%</span></div>
                  </div>
                  <div class="col-span-3 flex gap-2.5 text-[11px] text-ink-sub">
                    <span class="flex items-center gap-1"><span class="h-1 w-2.5 bg-primary-solid" />省内</span>
                    <span class="flex items-center gap-1"><span class="h-1 w-2.5" :style="{ background: RING.eff }" />省外</span>
                    <span class="flex items-center gap-1"><span class="h-1 w-2.5" style="background: var(--c-text-faint)" />其他</span>
                  </div>
                </div>
              </div>
              <div v-else class="mt-2.5 h-[480px] animate-pulse rounded-lg bg-subtle" />
            </template>

            <!-- 公开状态矩阵 -->
            <template v-else-if="ov">
              <div class="mt-3 mb-2.5 flex flex-wrap gap-2.5 text-[12px]" data-testid="pub-counts">
                <div class="flex items-center gap-2 rounded-md bg-danger-soft px-3 py-1.5 text-danger-ink"><b class="text-[16px]">{{ ov.publication.counts.np }}</b>该公开未公开</div>
                <div class="flex items-center gap-2 rounded-md bg-warning-soft px-3 py-1.5 text-warning-ink"><b class="text-[16px]">{{ ov.publication.counts.low }}</b>发了没人看(查阅率 &lt; 30%)</div>
                <div class="flex items-center gap-2 rounded-md bg-ai-soft px-3 py-1.5 text-ai-ink"><b class="text-[16px]">{{ ov.publication.counts.cmt }}</b>意见集中未答复</div>
              </div>
              <div class="grid grid-cols-[minmax(128px,1.4fr)_repeat(7,minmax(0,1fr))] gap-1 text-[12px]" data-testid="pub-matrix">
                <div class="px-1 py-1.5 text-[11px] text-ink-muted">指标 \ 受众</div>
                <div v-for="a in ov.publication.audiences" :key="a" class="px-1 py-1.5 text-center font-medium text-ink-sub">{{ a }}</div>
                <template v-for="r in ov.publication.rows" :key="r.name">
                  <div class="flex items-center gap-1.5 px-1 py-[7px]" :class="r.internal ? 'opacity-60' : ''">
                    <span class="font-medium text-ink">{{ r.name }}</span>
                    <span class="flex-none rounded-[3px] border border-line px-1 text-[10px] whitespace-nowrap text-ink-muted">{{ r.grp }}</span>
                  </div>
                  <div
                    v-for="(c, i) in r.cells"
                    :key="i"
                    class="flex min-h-[34px] items-center justify-center rounded px-1 py-1 text-center text-[11px] leading-[1.3] font-medium"
                    :class="[CELL[c.status], r.internal ? 'opacity-60' : '']"
                    :data-cell="c.status"
                  >{{ c.text }}</div>
                </template>
              </div>
            </template>
          </section>

          <!-- 快捷专区 -->
          <div class="grid grid-cols-5 gap-2.5" data-testid="shortcuts">
            <button
              v-for="s in ov?.shortcuts ?? []"
              :key="s.kind"
              type="button"
              class="ck-card cursor-pointer px-3 py-2.5 text-left transition-all hover:-translate-y-0.5 hover:border-primary"
              :class="s.kind === 'freq' && focusFreq ? '!border-primary' : ''"
              :aria-pressed="s.kind === 'freq' ? focusFreq : undefined"
              :data-shortcut="s.kind"
              @click="shortcut(s.kind)"
            >
              <div class="font-semibold text-ink">{{ s.title }}</div>
              <div class="mt-0.5 text-[11px] text-ink-muted">{{ s.sub }}</div>
            </button>
          </div>
        </div>

        <!-- 右侧：详情 / 机构排名 + 公开状态 -->
        <div class="flex flex-col gap-3">
          <section v-if="layer !== 'out'" class="ck-card flex flex-col gap-3.5 px-4 py-3.5" data-testid="detail">
            <template v-if="det">
              <div>
                <div class="flex items-center gap-2">
                  <span class="font-mono font-semibold text-primary" data-testid="detail-code">{{ det.code }}</span>
                  <Tag v-if="det.isKey" tone="danger">关键少数病组</Tag>
                </div>
                <div class="mt-0.5 text-[15px] font-semibold text-ink">{{ det.name }}</div>
                <div class="text-[11px] text-ink-muted">{{ det.scope }}</div>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <div class="rounded-lg bg-subtle px-2.5 py-2"><div class="text-[11px] text-ink-muted">病例数</div><div class="text-[16px] font-semibold text-ink tabular-nums" data-testid="detail-cases">{{ fmt(det.cases) }}</div></div>
                <div class="rounded-lg bg-subtle px-2.5 py-2"><div class="text-[11px] text-ink-muted">例均基金差额</div><div class="text-[16px] font-semibold tabular-nums" :class="det.avgDiff > 0 ? 'text-danger' : 'text-success'">{{ signed(det.avgDiff) }} 元</div></div>
                <div class="rounded-lg bg-subtle px-2.5 py-2"><div class="text-[11px] text-ink-muted">次均总费用</div><div class="text-[16px] font-semibold text-ink tabular-nums">{{ fmt(det.avgCost) }} 元</div></div>
                <div class="rounded-lg bg-subtle px-2.5 py-2"><div class="text-[11px] text-ink-muted">差额总额</div><div class="text-[16px] font-semibold tabular-nums" :class="det.avgDiff > 0 ? 'text-danger' : 'text-success'" data-testid="detail-total">{{ det.direction }} {{ wanText(det.totalWan) }}</div></div>
              </div>
              <div>
                <div class="mb-1.5 text-[12px] font-semibold text-ink">费用结构</div>
                <div class="grid grid-cols-[44px_1fr] items-center gap-x-2 gap-y-1.5 text-[11px] text-ink-sub">
                  <span>本组</span>
                  <div class="flex h-3 overflow-hidden rounded-[3px]"><span v-for="(m, i) in det.costMix" :key="m.name" :style="{ width: `${m.mine}%`, background: MIX_COLORS[i] }" :title="`${m.name} ${m.mine}%`" /></div>
                  <span>标杆组</span>
                  <div class="flex h-3 overflow-hidden rounded-[3px]"><span v-for="(m, i) in det.costMix" :key="m.name" :style="{ width: `${m.bench}%`, background: MIX_COLORS[i] }" :title="`${m.name} ${m.bench}%`" /></div>
                </div>
                <div class="mt-1.5 flex flex-wrap gap-x-2.5 gap-y-1 text-[11px] text-ink-sub">
                  <span v-for="(m, i) in det.costMix" :key="m.name" class="flex items-center gap-1"><span class="size-2 rounded-[2px]" :style="{ background: MIX_COLORS[i] }" />{{ m.name }} {{ m.mine }}%</span>
                </div>
              </div>
              <div>
                <div class="mb-1.5 flex justify-between text-[12px]"><span class="font-semibold text-ink">同级分位</span><span class="text-[11px] text-ink-muted">全省 8 个统筹区同组 · 次均费用</span></div>
                <PercentileBar :pct="det.peerPct" :tone="det.peerLevel" :height="14" ticks />
                <div class="mt-0.5 text-[12px] text-ink">本统筹区位于 <b :class="PEER_COLOR[det.peerLevel]">P{{ det.peerPct }}</b></div>
              </div>
              <div>
                <div class="mb-1 text-[12px] font-semibold text-ink">行为归因</div>
                <div class="grid grid-cols-[1fr_56px_52px] border-b border-divider py-1 text-[11px] text-ink-muted"><span>诊疗行为</span><span class="text-right">发生率</span><span class="text-right">费用倍率</span></div>
                <div v-for="b in det.behaviors" :key="b.name" class="grid grid-cols-[1fr_56px_52px] border-b border-divider py-[5px] text-[12px] text-ink">
                  <span>{{ b.name }}</span><span class="text-right tabular-nums">{{ b.ratePct }}%</span><span class="text-right font-medium text-warning tabular-nums">×{{ b.multiplier.toFixed(1) }}</span>
                </div>
              </div>
              <div>
                <div class="mb-1 text-[12px] font-semibold text-ink">标杆差距</div>
                <div v-for="g in det.gaps" :key="g.metric" class="grid grid-cols-[64px_1fr_1fr_52px] gap-1 border-b border-divider py-[5px] text-[12px]">
                  <span class="text-ink-sub">{{ g.metric }}</span><span class="text-ink tabular-nums">{{ g.value }}</span>
                  <span class="text-success tabular-nums">标杆 {{ g.bench }}</span><span class="text-right tabular-nums" :class="g.worse ? 'text-danger' : 'text-success'">{{ g.gap }}</span>
                </div>
              </div>
            </template>
            <div v-else class="h-[520px] animate-pulse rounded-lg bg-subtle" />
          </section>

          <Panel v-else-if="off?.ranking" title="就医地机构排名" data-testid="ranking">
            <template #head><Tag tone="muted">仅医保局可见</Tag></template>
            <div class="-mt-1.5 mb-2 text-[11px] text-ink-muted">不进入发布区;医疗机构门户只显示地区汇总</div>
            <div class="grid grid-cols-[20px_1fr_auto] gap-y-[7px] text-[12px]">
              <template v-for="(r, i) in off.ranking" :key="r.facility">
                <span class="text-ink-faint">{{ i + 1 }}</span><span class="text-ink">{{ r.facility }}</span><span class="text-right text-ink tabular-nums">{{ fmt(r.visits) }} 人次</span>
              </template>
            </div>
          </Panel>

          <Panel v-if="ov" title="公开状态" data-testid="pub-status">
            <template #head><button type="button" class="cursor-pointer text-[12px] text-primary" data-testid="open-pub" @click="layer = 'pub'">公开目录矩阵 →</button></template>
            <div class="grid grid-cols-[72px_1fr] gap-y-2 text-[12px]">
              <span class="text-ink-muted">公开对象</span><span class="text-ink">{{ ov.publication.status.target }}</span>
              <span class="text-ink-muted">对标档位</span><span class="text-ink">{{ ov.publication.status.tier }}</span>
              <span class="text-ink-muted">最近期次</span><span class="text-ink">{{ ov.publication.status.latest }}</span>
              <span class="text-ink-muted">签收率</span>
              <div class="flex items-center gap-2"><div class="h-1.5 flex-1 rounded-[3px] bg-divider"><div class="h-1.5 rounded-[3px] bg-success-solid" :style="{ width: `${ov.publication.status.signPct}%` }" /></div><span class="tabular-nums">{{ ov.publication.status.signPct }}%</span></div>
              <span class="text-ink-muted">查阅率</span>
              <div class="flex items-center gap-2"><div class="h-1.5 flex-1 rounded-[3px] bg-divider"><div class="h-1.5 rounded-[3px] bg-primary-solid" :style="{ width: `${ov.publication.status.readPct}%` }" /></div><span class="tabular-nums">{{ ov.publication.status.readPct }}%</span></div>
              <span class="text-ink-muted">意见 / 答复</span>
              <span class="text-ink">{{ ov.publication.status.opinions }} 条 · 答复率 <b class="text-warning">{{ ov.publication.status.replyPct }}%</b>({{ ov.publication.status.overdue }} 条超期)</span>
            </div>
          </Panel>
        </div>
      </div>
      <div v-if="ticker.length" class="ck-card mt-3 flex items-center gap-3 px-4 py-2 text-[12px] text-ink-sub" data-testid="ck-ticker">
        <span class="flex flex-none items-center gap-1.5 font-semibold text-primary"><i class="ck-live" />实时播报</span>
        <div class="ck-ticker min-w-0 flex-1"><div><span v-for="(t, i) in ticker" :key="i">{{ t }}</span></div></div>
      </div>
    </template>
  </div>
</template>
