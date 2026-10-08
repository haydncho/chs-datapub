<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { PageHeader } from '@/components/yb'
import { getJson, runAction, usePageData } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import { A4_SEED, type A4Data, type A4Group, type A4Indicator, type A4Status, type A4Tier } from '@/mock/A4'
import Catalog from './A4/Catalog.vue'
import IndicatorTable from './A4/IndicatorTable.vue'
import IndicatorDetail from './A4/IndicatorDetail.vue'
import IndicatorWizard from './A4/IndicatorWizard.vue'
import {
  COLUMNS, DEFAULT_WIDTHS, DENSITY, DENSITY_PAD, GROUP_NAME, HIDEABLE, SOURCE_FILTERS,
  type A4Row,
} from './A4/meta'

const data = usePageData('A4', A4_SEED)
const inds = computed(() => data.value.indicators)

/** re-read the read model after an accepted action (pending tiers, package, new indicators live on the server) */
async function refresh() {
  try {
    const remote = await getJson<A4Data>('/pages/A4')
    if (remote && typeof remote === 'object') data.value = { ...A4_SEED, ...remote }
  } catch { /* keep the current view */ }
}

// ── state ──
const grp = ref<A4Group | '全部'>('全部')
const dom = ref<string | null>(null)
const isrc = ref('全部')
const isel = ref(0)
const checked = ref<Record<number, boolean>>({})
const confirm = ref<A4Tier | null>(null)
const busy = ref(false)
const sort = ref<{ k: number; d: 1 | -1 }>({ k: -1, d: 1 })
const widths = ref<Record<number, number>>({})
const hidden = ref<Record<number, boolean>>({})
const dens = ref(1)
const colOpen = ref(false)
const wizOpen = ref(false)

// ── filtering / sorting ──
const srcKey = computed(() => SOURCE_FILTERS.find(f => f.label === isrc.value)?.source ?? null)
const srcOk = (r: A4Indicator) => !srcKey.value || r.source === srcKey.value

/** business order for the coded columns (not the alphabetical order of the internal keys) */
const TIER_ORDER: Record<A4Tier, number> = { pct: 0, anon: 1, named: 2, none: 3 }
const STATUS_ORDER: Record<A4Status, number> = { on: 0, review: 1, draft: 2, hold: 3 }
function sortValue(ind: A4Indicator, key: keyof A4Indicator): string | number {
  if (key === 'tier') return TIER_ORDER[ind.tier]
  if (key === 'status') return STATUS_ORDER[ind.status]
  if (key === 'version') return Number(ind.version.replace(/[^\d.]/g, '').split('.').map(x => x.padStart(3, '0')).join('')) || 0
  const v = ind[key]
  return typeof v === 'number' ? v : String(v ?? '')
}

const rows = computed<A4Row[]>(() => {
  const so = sort.value
  const key = so.k >= 0 ? COLUMNS[so.k]!.key : null
  return inds.value
    .map((ind, i) => ({ ind, i }))
    .filter(({ ind }) => (grp.value === '全部' || ind.group === grp.value) && (!dom.value || ind.domain === dom.value) && srcOk(ind))
    .sort((a, b) => {
      if (!key) return 0
      const x = sortValue(a.ind, key), y = sortValue(b.ind, key)
      return (typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y), 'zh')) * so.d
    })
    .map(({ ind, i }) => ({
      i,
      ind,
      intl: ind.source === 'internal',
      checked: !!checked.value[i],
      selected: i === isel.value,
      pending: ind.pendingTier ?? null,
    }))
})

const scope = computed(
  () => (dom.value || (grp.value === '全部' ? '全部指标' : GROUP_NAME[grp.value])) + (isrc.value !== '全部' ? ' · ' + isrc.value : ''),
)
/** only rows visible under the current filter count (and get added) — hidden checks are dropped */
const checkedRows = computed(() => rows.value.filter(r => r.checked))
const nCk = computed(() => checkedRows.value.length)
watch(rows, rs => {
  const visible = new Set(rs.map(r => r.i))
  const kept = Object.fromEntries(Object.entries(checked.value).filter(([k, v]) => v && visible.has(Number(k))))
  if (Object.keys(kept).length !== Object.values(checked.value).filter(Boolean).length) checked.value = kept
  // the selected indicator was filtered out: follow the filter to its first row
  if (rs.length && !visible.has(isel.value)) select(rs[0]!.i, false)
})

// ── columns: widths, visibility, density ──
const W = computed(() => ({ ...DEFAULT_WIDTHS, ...widths.value }))
const gridTemplate = computed(
  () => '18px minmax(140px,1fr) ' + [1, 2, 3, 4, 5, 6].filter(i => !hidden.value[i]).map(i => W.value[i] + 'px').join(' '),
)

let stopDrag: (() => void) | null = null
function startResize(ci: number, e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  const x0 = e.clientX
  const w0 = W.value[ci]!
  const mv = (ev: MouseEvent) => {
    widths.value = { ...widths.value, [ci]: Math.max(30, Math.round(w0 + ev.clientX - x0)) }
  }
  const up = () => {
    window.removeEventListener('mousemove', mv)
    window.removeEventListener('mouseup', up)
    stopDrag = null
  }
  window.addEventListener('mousemove', mv)
  window.addEventListener('mouseup', up)
  stopDrag = up
}
onBeforeUnmount(() => stopDrag?.())

function toggleSort(ci: number) {
  const on = sort.value.k === ci
  sort.value = { k: ci, d: on ? (-sort.value.d as 1 | -1) : 1 }
}
function resetCols() {
  widths.value = {}
  hidden.value = {}
}

// ── row interactions ──
const detailEl = ref<{ $el?: HTMLElement } | null>(null)
function select(i: number, scroll = true) {
  isel.value = i
  confirm.value = null
  // 窄屏下详情在列表下方,点选后滚动到详情
  if (scroll && window.innerWidth < 1280) nextTick(() => detailEl.value?.$el?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}
function toggleCheck(r: A4Row) {
  if (r.intl) {
    say('仅内部指标不可加入发布包')
    return
  }
  checked.value = { ...checked.value, [r.i]: !r.checked }
}
function setFilter(g: A4Group | '全部', d: string | null) {
  grp.value = g
  dom.value = d
}
function clearFilters() {
  grp.value = '全部'
  dom.value = null
  isrc.value = '全部'
}
async function addPkg() {
  if (busy.value) return
  const names = checkedRows.value.map(r => r.ind.name)
  busy.value = true
  const r = await runAction<{ result?: { added: string[] } }>('A4', 'addToPackage', { indicators: names, package: data.value.targetPackage })
  busy.value = false
  if (!r.ok) return say(r.error)
  checked.value = {}
  await refresh()
  const n = r.data?.result?.added.length ?? names.length
  say(`已将 ${n} 项指标加入 ${data.value.targetPackage}` + (n < names.length ? `(${names.length - n} 项此前已在包内)` : ''))
}

// ── detail: tier change approval ──
const ind = computed(() => inds.value[isel.value] ?? inds.value[0]!)
function pickTier(t: A4Tier) {
  if (ind.value.source === 'internal') return
  if (ind.value.pendingTier) {
    say('已有待审批的档位变更,请等待召集人审批')
    return
  }
  if (ind.value.tier !== t) confirm.value = t
}
async function confirmOk() {
  if (!confirm.value || busy.value) return
  busy.value = true
  const r = await runAction('A4', 'requestTierChange', { indicator: ind.value.name, from: ind.value.tier, to: confirm.value })
  busy.value = false
  if (!r.ok) return say(r.error)
  confirm.value = null
  await refresh()
  say('已提交召集人审批 · 审批前按原档位发布')
}
async function onSubmitted() {
  await refresh()
}
</script>

<template>
  <!--
    ≥1280:目录 | 标题 + 表格 | 详情 三栏。
    1024–1279(横屏 Pad):目录左栏常驻;标题、表格、详情在右侧依次排列。
    <1024(竖屏 Pad):标题 → 目录(紧凑筛选卡)→ 表格 → 详情,单列。
  -->
  <section data-screen-label="A4 指标配置" class="grid min-h-[calc(var(--app-vh)-132px)] grid-cols-1 lg:grid-cols-[208px_minmax(0,1fr)] lg:grid-rows-[auto_auto_1fr] xl:grid-cols-[220px_minmax(0,1fr)_340px] xl:grid-rows-[auto_1fr]">
    <Catalog
      class="max-lg:order-2 lg:row-span-3 xl:row-span-2"
      :indicators="inds"
      :grp="grp"
      :dom="dom"
      :isrc="isrc"
      @filter="setFilter"
      @source="isrc = $event"
    />

    <div class="min-w-0 px-7 pt-6 max-xl:px-5 max-lg:order-1 max-lg:pb-4 lg:col-start-2 lg:row-start-1">
      <PageHeader title="指标配置">
        <template #subtitle>
          {{ scope }} · {{ rows.length }} 项 · 全库 {{ data.libraryTotal }} 项:国家底稿 {{ data.libraryNational }} · 地方增选 {{ data.libraryLocal }} · 仅内部 {{ data.libraryInternal }}
        </template>
        <Button v-if="nCk > 0" variant="soft" class="px-3.5 font-medium" :disabled="busy" @click="addPkg">加入发布包 · {{ nCk }}</Button>
        <Popover v-model:open="colOpen">
          <PopoverTrigger as-child>
            <Button variant="outline" class="gap-1.5 px-3 font-normal text-ink-2">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="#445066" stroke-width="2" stroke-linecap="round"><path d="M4 5h16M4 12h16M4 19h16M9 3v18M15 3v18" /></svg>列与密度
            </Button>
          </PopoverTrigger>
          <PopoverContent
            align="end"
            :side-offset="6"
            class="z-20 flex w-[240px] flex-col gap-2.5 rounded-xl border-line-1 p-3 shadow-[0_12px_32px_rgba(15,23,42,.12)]"
          >
            <div class="text-[11px] font-semibold text-ink-5 max-xl:text-xs">显示列</div>
            <label v-for="[ci, l] in HIDEABLE" :key="ci" class="flex cursor-pointer items-center gap-2 text-[13px] max-xl:min-h-10 max-xl:gap-3">
              <Checkbox
                :model-value="!hidden[ci]"
                class="size-4 rounded-[4px] border-[1.5px] border-ink-6 bg-white shadow-none max-xl:size-5 data-[state=checked]:border-brand data-[state=checked]:bg-brand"
                @update:model-value="v => (hidden = { ...hidden, [ci]: !v })"
              >
                <span class="text-[11px] leading-none text-white">✓</span>
              </Checkbox>
              {{ l }}
            </label>
            <div class="border-t border-line-2 pt-2.5 text-[11px] font-semibold text-ink-5 max-xl:text-xs">行密度</div>
            <div class="flex rounded-lg bg-surface-3 p-[3px]">
              <button
                v-for="(l, i) in DENSITY"
                :key="l"
                type="button"
                :class="cn(
                  'flex-1 cursor-pointer rounded-md py-1 text-center text-xs whitespace-nowrap max-xl:py-2.5',
                  i === dens ? 'bg-white text-ink-1 shadow-[0_1px_2px_rgba(15,23,42,.1)]' : 'text-ink-4',
                )"
                @click="dens = i"
              >{{ l }}</button>
            </div>
            <button type="button" class="cursor-pointer self-start text-xs text-brand max-xl:min-h-10" @click="resetCols">恢复默认列宽</button>
          </PopoverContent>
        </Popover>
        <Button @click="wizOpen = true">+ 新建指标</Button>
      </PageHeader>
    </div>

    <main class="flex min-w-0 flex-col px-7 pt-4 pb-12 max-xl:px-5 max-xl:pb-6 max-lg:order-3 lg:col-start-2 lg:row-start-2">
      <IndicatorTable
        :rows="rows"
        :grid-template="gridTemplate"
        :pad="DENSITY_PAD[dens]!"
        :hidden="hidden"
        :sort="sort"
        @select="select"
        @check="toggleCheck"
        @sort="toggleSort"
        @resize="startResize"
        @clear="clearFilters"
      />
    </main>

    <IndicatorDetail
      ref="detailEl"
      class="scroll-mt-20 max-lg:order-4 lg:col-start-2 lg:row-start-3 xl:col-start-3 xl:row-span-2 xl:row-start-1"
      :ind="ind"
      :audiences="data.audiences"
      :confirm="confirm"
      :pending="ind.pendingTier ?? null"
      :busy="busy"
      :package-name="data.targetPackage"
      @pick-tier="pickTier"
      @confirm-ok="confirmOk"
      @confirm-no="confirm = null"
    />

    <IndicatorWizard v-model:open="wizOpen" :wizard="data.wizard" :existing="inds.map(x => x.name)" @submitted="onSubmitted" />
  </section>
</template>
