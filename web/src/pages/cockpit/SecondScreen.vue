<script setup lang="ts">
import { computed } from 'vue'
import { sign, wan } from '@/lib/format'
import AnimatedNumber from './AnimatedNumber.vue'
import PanelTitle from './PanelTitle.vue'
import PeerView from './PeerView.vue'
import PubMatrix from './PubMatrix.vue'
import ScreenTitle from './ScreenTitle.vue'
import { K, useCockpit } from './store'

/**
 * 双屏拼接的副屏(1920×1080),与主屏同一套栅格:主标题 · 6 张专属指标卡 · 主视图 + 右栏 · 下排三块。
 * 医保局:公开与反馈(公开矩阵、受众查阅率、异地流向、区县机构概况);本院:运行与对标(同级对标、科室差额、病组规模)。
 * 原则:副屏只放主屏没有的内容 —— 主屏已有的面板与数字(实时提醒、闭环、待办数)不再出现;
 * 双屏时主屏的视图切换也不再包含副屏这些视图(见 store 的 SCREEN2_VIEWS)。指标卡数字全部由页面数据现算。
 */
const { s, I, data, clock, bubble, span, insts, deptsP, peerStats, savedSub } = useCockpit()
const hosp = computed(() => I.value.id === 'hosp')
const panel = 'rounded-[10px] border border-[rgb(var(--ck-line)/.16)] bg-[rgb(var(--ck-panel)/.66)] px-[22px] py-3.5'
const caption = computed(() => (hosp.value ? 'HOSPITAL OPERATION · BENCHMARK' : 'OPEN DATA · FEEDBACK MONITOR'))

/* ---------- 公开矩阵统计(医保局) */
const PUBLISHED = ['ok', 'low', 'cmt']
const pub = computed(() => {
  const d = data.value
  const rows = d.matrix.filter(r => !r.internal)
  const cells = rows.flatMap(r => r.cells)
  const applicable = cells.filter(c => c.status !== 'na' && c.status !== 'int')
  const published = applicable.filter(c => PUBLISHED.includes(c.status))
  const reads = cells.filter(c => (c.status === 'ok' || c.status === 'low') && c.value != null).map(c => c.value!)
  const avg = (xs: number[]) => (xs.length ? Math.round(xs.reduce((a, x) => a + x, 0) / xs.length) : 0)
  const audiences = d.audiences.map((a, j) => {
    const col = rows.map(r => r.cells[j]!).filter(Boolean)
    const v = avg(col.filter(c => (c.status === 'ok' || c.status === 'low') && c.value != null).map(c => c.value!))
    return {
      a, v,
      np: col.filter(c => c.status === 'np').length,
      c: v >= 80 ? K.sky : v >= 60 ? 'rgb(var(--ck-acc)/.55)' : K.amber,
      vc: v >= 80 ? K.skyLite : v >= 60 ? K.ink2 : K.amber,
    }
  })
  return {
    rows: rows.length,
    coverage: applicable.length ? Math.round((published.length / applicable.length) * 100) : 0,
    publishedN: published.length, applicableN: applicable.length,
    avgRead: avg(reads),
    unanswered: cells.filter(c => c.status === 'cmt').reduce((a, c) => a + (c.value ?? 0), 0),
    fullRows: rows.filter(r => r.cells.every(c => c.status === 'na' || PUBLISHED.includes(c.status))).length,
    audiences,
  }
})

/* ---------- 6 张专属指标卡(不与主屏指标卡 / 闭环 / 待办数字重复) */
interface Card { k: string; v: string; u: string; sub: string; c: string }
const cards = computed<Card[]>(() => {
  const d = data.value
  if (hosp.value) {
    const over = deptsP.value.filter(x => x.diff > 0).length
    const ps = peerStats.value
    return [
      { k: '优于同级中位', v: String(ps.better.length), u: '/ ' + ps.n + ' 项', sub: `差于中位 ${ps.worse.length} 项 · 同级 6 家匿名`, c: K.green },
      { k: 'CMI 同级分位', v: ps.cmi?.pct ?? '—', u: '', sub: '同级分位 · 越高越好', c: K.green },
      { k: '例均差额分位', v: ps.diff?.pct ?? '—', u: '', sub: '同级分位 · 越高越好', c: K.amber },
      { k: '差于同级中位', v: String(ps.worse.length), u: '/ ' + ps.n + ' 项', sub: ps.worse.map(x => x.name).join(' · ') || '无', c: K.amber },
      { k: '超支科室', v: String(over), u: '/ ' + deptsP.value.length + ' 个', sub: '结余科室 ' + (deptsP.value.length - over) + ' 个', c: K.red },
      { k: '重点关注', v: ps.watch, u: '', sub: '同级分位最低的一项', c: K.redSoft },
    ]
  }
  const p = pub.value
  const out = d.flows.filter(f => f.scope === '省外').reduce((a, f) => a + f.share, 0)
  const people = /([\d,]+)\s*人次/.exec(d.flowOrigin.sub)?.[1] ?? '—'
  return [
    { k: '公开指标', v: String(p.rows), u: '项', sub: '受众 ' + d.audiences.length + ' 类 · 另 1 项仅内部', c: K.sky },
    { k: '公开覆盖率', v: String(p.coverage), u: '%', sub: `已公开 ${p.publishedN} / 应公开 ${p.applicableN} 格`, c: p.coverage >= 90 ? K.green : K.amber },
    { k: '全受众已公开', v: String(p.fullRows), u: '/ ' + p.rows + ' 项', sub: '对所有适用受众都已公开的指标', c: K.green },
    { k: '平均查阅率', v: String(p.avgRead), u: '%', sub: '已公开格的平均查阅率', c: p.avgRead >= 80 ? K.green : K.amber },
    { k: '意见未复', v: String(p.unanswered), u: '条', sub: '受众对已公开指标的意见', c: K.red },
    { k: '异地就医人次', v: people, u: '人次', sub: `省外占 ${out.toFixed(1)}%`, c: K.amberHot },
  ]
})

/* ---------- 异地就医流向 TOP(医保局) */
const flowRows = computed(() => {
  const mx = Math.max(...data.value.flows.map(f => f.share), 1)
  return data.value.flows.map(f => ({
    ...f, w: ((f.share / mx) * 100).toFixed(1) + '%',
    c: f.scope === '省内' ? K.sky : f.scope === '省外' ? K.amberHot : K.ink4,
  }))
})

/* ---------- 科室差额排行(本院):左结余、右超支 */
const deptRows = computed(() => {
  const ds = [...deptsP.value].sort((a, b) => b.diff - a.diff)
  const mx = Math.max(...ds.map(x => Math.abs(x.diff)), 1)
  return ds.map(x => ({ ...x, v: sign(x.diff), w: ((Math.abs(x.diff) / mx) * 100).toFixed(1) + '%', over: x.diff > 0 }))
})

/* ---------- 订阅推送(主屏只有入口按钮,没有展示当前订阅) */
const push = computed(() => {
  const x = savedSub.value
  return x ? { on: x.enabled, when: x.frequency, ch: x.channel, contents: x.contents, to: x.recipients } : null
})

/* ---------- 区县机构概况(医保局):机构数 · 逆差机构占比 · 逆差最大的机构(主屏机构矩阵只有逐家色块,没有这组汇总) */
const districts = computed(() =>
  data.value.districts.map(dist => {
    const list = insts.value.filter(x => x.district === dist)
    const n = list.length || 1
    const deficit = list.filter(x => x.diff > 0)
    const worst = [...list].sort((a, b) => b.diff - a.diff)[0]
    return {
      d: dist, n: list.length,
      def: deficit.length, w: ((deficit.length / n) * 100).toFixed(0) + '%',
      worst: worst && worst.diff > 0 ? worst.name : '—', wv: worst && worst.diff > 0 ? sign(worst.diff) : '',
    }
  }),
)

/* ---------- 病组四象限汇总:各象限病组数与差额合计(主屏气泡图只有散点,没有汇总) */
const quads = computed(() => {
  const items = bubble.value.items
  const xs = items.map(i => i.x).sort((a, b) => a - b)
  const med = xs[Math.floor(xs.length / 2)] ?? 0
  const Q = [
    { k: '低量 · 逆差', f: (i: { x: number; y: number }) => i.x < med && i.y > 0, c: K.redSoft },
    { k: '高量 · 逆差', f: (i: { x: number; y: number }) => i.x >= med && i.y > 0, c: K.red, key: true },
    { k: '低量 · 结余', f: (i: { x: number; y: number }) => i.x < med && i.y <= 0, c: '#5A9C84' },
    { k: '高量 · 结余', f: (i: { x: number; y: number }) => i.x >= med && i.y <= 0, c: K.green },
  ]
  return Q.map(q => {
    const g = items.filter(q.f)
    const tot = g.reduce((a, i) => a + i.tot, 0)
    const top = [...g].sort((a, b) => Math.abs(b.tot) - Math.abs(a.tot))[0]
    return { k: q.k, c: q.c, key: !!q.key, n: g.length, tot: (tot > 0 ? '逆差 ' : '结余 ') + wan(tot), top: top ? top.k : '—' }
  })
})

/* ---------- 病组规模分布(本院):按本院病例数分档 */
const sizes = computed(() => {
  const items = bubble.value.items
  const total = items.reduce((a, i) => a + i.x, 0) || 1
  // 分档按月均病例数(季 / 年口径按期内月数折算),与气泡图「病例 < 30/月 并入其他」同一口径
  const m = span.value
  const B = [
    { k: `≥ ${100 * m} 例`, f: (x: number) => x >= 100 * m },
    { k: `${50 * m}–${100 * m - 1} 例`, f: (x: number) => x >= 50 * m && x < 100 * m },
    { k: `${30 * m}–${50 * m - 1} 例`, f: (x: number) => x >= 30 * m && x < 50 * m },
    { k: `< ${30 * m} 例`, f: (x: number) => x < 30 * m, note: '门户中并入其他' },
  ]
  const rows = B.map(b => {
    const g = items.filter(i => b.f(i.x))
    const cases = g.reduce((a, i) => a + i.x, 0)
    return { k: b.k, note: b.note ?? '', n: g.length, share: (cases / total) * 100 }
  })
  const mx = Math.max(...rows.map(r => r.share), 1)
  return rows.map(r => ({ ...r, sh: r.share.toFixed(1), w: ((r.share / mx) * 100).toFixed(1) + '%' }))
})

const LEGEND = [
  ['rgba(46,209,138,.13)', '#5BE0A6', '已公开 · 查阅率'],
  ['rgba(245,183,78,.15)', '#F5B74E', '查阅率低'],
  ['rgba(255,90,78,.17)', '#FF7A6E', '未公开'],
  ['rgba(160,130,255,.17)', '#B9A2FF', '意见未复'],
  ['rgba(255,255,255,.04)', '#6F84A6', '仅内部'],
] as const
</script>

<template>
  <!-- 主标题(与主屏同一套 2.5D 设计) -->
  <ScreenTitle :title="I.screen2Title" :caption="caption" />
  <div class="absolute top-0 right-7 left-7 flex h-[72px] items-center">
    <div class="flex w-[600px] items-center gap-3.5">
      <div class="flex h-9 items-center rounded-lg border border-[rgb(var(--ck-line)/.35)] px-2.5 text-sm font-semibold text-[rgb(var(--ck-accl))]">副屏</div>
      <div class="leading-[1.3]">
        <div class="text-[17px] font-semibold">{{ I.org }}</div>
        <div class="text-sm text-[#6F84A6]">双屏拼接 · {{ I.zone }}</div>
      </div>
    </div>
    <div class="flex-1" />
    <div class="w-[600px] text-right leading-[1.2]">
      <div class="yb-num text-[28px] font-medium tracking-[1px]">{{ clock.time }}</div>
      <div class="text-sm text-[#6F84A6]">{{ clock.date }} · 数据截至 {{ data.dataAsOf }}</div>
    </div>
  </div>

  <!-- 专属指标卡 -->
  <div class="absolute top-[84px] right-7 left-7 grid h-[100px] grid-cols-6 rounded-[10px] border border-[rgb(var(--ck-line)/.18)] ck-ribbon">
    <div
      v-for="(c, i) in cards"
      :key="c.k"
      :class="['relative flex flex-col justify-between border-l px-5 py-3', i === 0 ? 'border-transparent' : 'border-[rgb(var(--ck-line)/.14)]']"
    >
      <span class="truncate text-base text-[#9FB2D1]">{{ c.k }}</span>
      <div class="flex min-w-0 items-baseline gap-1">
        <AnimatedNumber v-if="/\d/.test(c.v) && c.v.length < 8" :value="c.v" :replay="s.pulse" class="yb-num text-[40px] leading-none font-semibold" :style="{ color: c.c }" />
        <span v-else class="truncate text-[26px] leading-none font-semibold" :style="{ color: c.c }" :title="c.v">{{ c.v }}</span>
        <span class="text-sm whitespace-nowrap text-[#9FB2D1]">{{ c.u }}</span>
      </div>
      <span class="truncate text-sm text-[#6F84A6]" :title="c.sub">{{ c.sub }}</span>
    </div>
  </div>

  <!-- 主视图:公开矩阵(医保局)/ 同级对标(本院) -->
  <div :class="[panel, 'absolute top-[200px] left-7 flex h-[564px] w-[1208px] flex-col px-6 py-4']">
    <div class="mb-3 flex items-center justify-between">
      <PanelTitle :title="I.screen2Right" bar="#3FD1A0" size="lg" />
      <div v-if="!hosp" class="flex items-center gap-3 text-sm text-[#9FB2D1]">
        <span v-for="[bg, fg, l] in LEGEND" :key="l" class="flex items-center gap-1.5">
          <span class="size-3 rounded-[3px] border" :style="{ background: bg, borderColor: fg }" />{{ l }}
        </span>
      </div>
    </div>
    <div class="flex min-h-0 flex-1 flex-col">
      <PubMatrix v-if="!hosp" />
      <PeerView v-else />
    </div>
  </div>

  <!-- 右栏 -->
  <div class="absolute top-[200px] left-[1252px] flex h-[564px] w-[640px] flex-col gap-4">
    <template v-if="!hosp">
      <!-- 受众查阅率:各受众已公开指标的平均查阅率(2.5D 柱) -->
      <div :class="[panel, 'flex min-h-0 flex-1 flex-col']">
        <div class="flex items-center justify-between">
          <PanelTitle title="受众查阅率" bar="rgb(var(--ck-acc))" />
          <span class="text-sm text-[#6F84A6]">已公开指标平均 · 低于 60% 标橙</span>
        </div>
        <div class="mt-2 flex min-h-0 flex-1 items-end gap-5 px-2 pt-6">
          <div v-for="(a, i) in pub.audiences" :key="a.a" class="flex h-full flex-1 flex-col items-center justify-end gap-1.5">
            <div
              class="relative w-full transition-[height] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]"
              :style="{ height: s.intro ? a.v + '%' : '0%', transitionDelay: i * 60 + 'ms' }"
            >
              <span class="yb-num absolute -top-6 left-1/2 -translate-x-1/2 text-base font-semibold whitespace-nowrap" :style="{ color: a.vc }">{{ a.v }}%</span>
              <div class="absolute top-[4px] right-[5px] bottom-0 left-0" :style="{ background: a.c }" />
              <div class="cube-side absolute inset-y-0 right-0 w-[5px]" :style="{ background: a.c }" />
              <div class="cube-top absolute inset-x-0 top-0 h-[4px]" :style="{ background: a.c }" />
            </div>
            <span class="text-sm whitespace-nowrap text-[#C9D6EA]">{{ a.a }}</span>
            <span :class="['text-sm leading-none whitespace-nowrap', a.np ? 'text-[#FF8A7E]' : 'text-transparent']">未公开 {{ a.np }}</span>
          </div>
        </div>
      </div>
      <!-- 异地就医流向 TOP -->
      <div :class="[panel, 'flex min-h-0 flex-1 flex-col']">
        <div class="flex items-center justify-between">
          <PanelTitle title="异地就医流向" bar="#F5A524" />
          <span class="text-sm text-[#6F84A6]">{{ data.flowOrigin.name }} · {{ data.flowOrigin.sub }}</span>
        </div>
        <div class="mt-1.5 flex flex-1 flex-col justify-around">
          <div v-for="(f, i) in flowRows" :key="f.name" class="flex items-center gap-3">
            <span class="w-[72px] shrink-0 truncate text-[15px] text-[#C9D6EA]">{{ f.name }}</span>
            <span class="w-[44px] shrink-0 rounded-[3px] border text-center text-sm leading-[20px]" :style="{ borderColor: f.c, color: f.c }">{{ f.scope }}</span>
            <div class="h-2.5 flex-1 rounded-[3px] bg-[rgba(255,255,255,.06)]">
              <div class="h-2.5 rounded-[3px] transition-[width] duration-[1000ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? f.w : '0%', background: f.c, boxShadow: `0 0 8px color-mix(in srgb,${f.c} 40%,transparent)`, transitionDelay: i * 70 + 'ms' }" />
            </div>
            <span class="yb-num w-[56px] shrink-0 text-right text-lg font-semibold" :style="{ color: f.c }">{{ f.share }}%</span>
            <span class="yb-num w-[72px] shrink-0 text-right text-sm text-[#9FB2D1]">{{ f.amount }}</span>
          </div>
        </div>
      </div>
    </template>
    <!-- 科室差额排行(本院):左结余、右超支 -->
    <div v-else :class="[panel, 'flex min-h-0 flex-1 flex-col']">
      <div class="flex items-center justify-between">
        <PanelTitle title="科室差额 · 超支与结余" bar="#FF6B5E" />
        <span class="text-sm text-[#6F84A6]">例均基金差额(元) · CMI</span>
      </div>
      <div class="mt-2 flex flex-1 flex-col justify-around">
        <div v-for="(x, i) in deptRows" :key="x.name" class="grid grid-cols-[92px_1fr_1fr_72px_56px] items-center gap-x-2.5">
          <span class="truncate text-[15px] text-[#C9D6EA]">{{ x.name }}</span>
          <div class="flex h-full items-center justify-end border-r border-[rgba(230,238,249,.3)]">
            <span v-if="!x.over" class="h-3.5 rounded-l-[2px] bg-[#3FD1A0] opacity-85 transition-[width] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? x.w : '0', transitionDelay: i * 50 + 'ms' }" />
          </div>
          <div class="flex h-full items-center">
            <span v-if="x.over" class="h-3.5 rounded-r-[2px] bg-[#FF6B5E] opacity-90 transition-[width] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? x.w : '0', transitionDelay: i * 50 + 'ms' }" />
          </div>
          <span class="yb-num text-right text-base font-semibold" :style="{ color: x.over ? '#FFB2AA' : '#9FE3C7' }">{{ x.v }}</span>
          <span class="yb-num text-right text-sm text-[#9FB2D1]">{{ x.cmi.toFixed(2) }}</span>
        </div>
      </div>
      <div class="mt-1 grid grid-cols-[92px_1fr_1fr_72px_56px] gap-x-2.5 text-sm text-[#6F84A6]">
        <span /><span class="text-right">← 结余</span><span>超支 →</span>
      </div>
    </div>
  </div>

  <!-- 下排:区县机构概况 / 病组规模分布 · 订阅推送 · 病组四象限汇总 -->
  <div :class="[panel, 'absolute top-[780px] left-7 flex h-[276px] w-[760px] flex-col']">
    <template v-if="!hosp">
      <div class="flex items-center justify-between">
        <PanelTitle title="区县机构概况" bar="rgb(var(--ck-accl))" />
        <span class="text-sm text-[#6F84A6]">逆差 = 例均基金差额 > 0</span>
      </div>
      <div class="mt-2 grid grid-cols-[64px_64px_1fr_220px] gap-x-3 text-sm text-[#6F84A6]">
        <span>区县</span><span class="text-right">机构</span><span>逆差机构占比</span><span>逆差最大的机构</span>
      </div>
      <div class="flex flex-1 flex-col justify-around">
        <div v-for="(x, i) in districts" :key="x.d" class="grid grid-cols-[64px_64px_1fr_220px] items-center gap-x-3 border-t border-[rgb(var(--ck-line)/.1)] pt-2">
          <span class="text-[15px] font-medium text-[#C9D6EA]">{{ x.d }}</span>
          <span class="yb-num text-right text-[15px] text-[#C9D6EA]">{{ x.n }} 家</span>
          <span class="flex items-center gap-2.5">
            <span class="h-2.5 flex-1 rounded-[3px] bg-[rgba(255,255,255,.06)]">
              <span class="block h-2.5 rounded-[3px] bg-[#FF6B5E] opacity-85 transition-[width] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? x.w : '0%', transitionDelay: i * 70 + 'ms' }" />
            </span>
            <span class="yb-num w-[88px] shrink-0 text-sm whitespace-nowrap text-[#FFB2AA]">{{ x.def }} 家 · {{ x.w }}</span>
          </span>
          <span class="truncate text-sm text-[#C9D6EA]" :title="x.worst">{{ x.worst }} <b class="yb-num font-semibold text-[#FFB2AA]">{{ x.wv }}</b></span>
        </div>
      </div>
    </template>
    <template v-else>
      <div class="flex items-center justify-between">
        <PanelTitle title="病组规模分布" bar="rgb(var(--ck-accl))" />
        <span class="text-sm text-[#6F84A6]">按本院病例数分档 · 条长为病例占比</span>
      </div>
      <div class="mt-2 flex flex-1 flex-col justify-around">
        <div v-for="(x, i) in sizes" :key="x.k" class="grid grid-cols-[88px_1fr_76px_88px] items-center gap-x-3">
          <span class="text-[15px] text-[#C9D6EA]">{{ x.k }}</span>
          <span class="h-3 rounded-[3px] bg-[rgba(255,255,255,.06)]">
            <span class="block h-3 rounded-[3px] transition-[width] duration-[900ms] ease-[cubic-bezier(.2,.8,.2,1)]" :style="{ width: s.intro ? x.w : '0%', background: x.note ? '#4E5F7E' : 'rgb(var(--ck-acc))', transitionDelay: i * 70 + 'ms' }" />
          </span>
          <span class="yb-num text-right text-lg font-semibold text-[rgb(var(--ck-accl))]">{{ x.sh }}%</span>
          <span class="text-right text-sm text-[#9FB2D1]">{{ x.n }} 个病组</span>
          <span v-if="x.note" class="col-start-2 col-end-5 -mt-1 text-sm text-[#6F84A6]">{{ x.note }}</span>
        </div>
      </div>
    </template>
  </div>

  <div :class="[panel, 'absolute top-[780px] left-[804px] flex h-[276px] w-[432px] flex-col']">
    <div class="flex items-center justify-between">
      <PanelTitle title="订阅推送" bar="#B9A2FF" />
      <span v-if="push?.on" class="flex items-center gap-1.5 text-sm text-[#3FD1A0]" data-testid="screen2-sub-state"><span class="size-2 rounded-full bg-[#3FD1A0]" />已开启</span>
      <span v-else class="flex items-center gap-1.5 text-sm text-[#6F84A6]" data-testid="screen2-sub-state"><span class="size-2 rounded-full bg-[#6F84A6]" />{{ push ? '已停用' : '未开启' }}</span>
    </div>
    <div v-if="push" :class="['mt-3 grid grid-cols-[64px_1fr] gap-x-3 gap-y-2.5 text-sm', !push.on && 'opacity-60']">
      <span class="text-[#6F84A6]">频率</span><span class="text-[15px] font-medium text-[#CDBDFF]">{{ push.when }}</span>
      <span class="text-[#6F84A6]">渠道</span><span class="text-[15px] font-medium text-[#CDBDFF]">{{ push.ch }}</span>
      <span class="pt-0.5 text-[#6F84A6]">内容</span>
      <span class="flex flex-wrap gap-1.5">
        <span v-for="c in push.contents" :key="c" class="rounded-[4px] bg-[rgba(160,130,255,.12)] px-2 py-0.5 text-[#CDBDFF]">{{ c }}</span>
      </span>
      <span class="pt-0.5 text-[#6F84A6]">接收人</span>
      <span class="flex flex-wrap gap-1.5">
        <span v-for="r in push.to" :key="r" class="rounded-[4px] bg-[rgba(255,255,255,.05)] px-2 py-0.5 text-[#C9D6EA]">{{ r }}</span>
      </span>
    </div>
    <div v-else class="flex flex-1 flex-col items-center justify-center gap-1.5 text-center text-sm text-[#6F84A6]">
      <span class="text-[15px] text-[#9FB2D1]">尚未订阅全景图推送</span>
      <span>在主屏点「订阅推送」设置频率、内容与接收人</span>
    </div>
  </div>

  <div :class="[panel, 'absolute top-[780px] left-[1252px] flex h-[276px] w-[640px] flex-col']">
    <div class="flex items-center justify-between">
      <PanelTitle title="病组四象限汇总" bar="#FF6B5E" />
      <span class="text-sm text-[#6F84A6]">以病例数中位为界 · 差额合计</span>
    </div>
    <div class="mt-2.5 grid flex-1 grid-cols-2 grid-rows-2 gap-2.5">
      <div
        v-for="q in quads"
        :key="q.k"
        class="relative flex flex-col justify-center overflow-hidden rounded-md border px-4"
        :style="{ borderColor: q.key ? q.c : 'rgb(var(--ck-line)/.12)', background: q.key ? 'rgba(255,107,94,.1)' : 'rgba(255,255,255,.03)' }"
      >
        <span class="absolute inset-y-0 left-0 w-[3px]" :style="{ background: q.c }" />
        <div class="flex items-baseline justify-between">
          <span class="text-[15px] font-medium" :style="{ color: q.c }">{{ q.k }}{{ q.key ? ' — 关键少数' : '' }}</span>
          <span class="text-sm text-[#9FB2D1]"><AnimatedNumber :value="String(q.n)" :replay="s.pulse" class="yb-num text-[22px] font-semibold text-[#E6EEF9]" /> 个病组</span>
        </div>
        <div class="mt-1 flex items-baseline justify-between text-sm text-[#9FB2D1]">
          <span class="yb-num text-lg font-semibold" :style="{ color: q.c }">{{ q.tot }}</span>
          <span>最大 <b class="yb-num font-semibold text-[#C9D6EA]">{{ q.top }}</b></span>
        </div>
      </div>
    </div>
  </div>
</template>
