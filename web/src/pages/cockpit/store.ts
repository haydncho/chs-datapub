import { computed, inject, reactive, type InjectionKey, type Ref } from 'vue'
import { fmt, hsh, pad, sign, wan } from '@/lib/format'
import { appearance } from '@/app/appearance'
import type {
  CockpitData, CockpitIdentity, CockpitPeer, CockpitPeriodView, CockpitSavedSubscription, CockpitView, IdentityId, LoopStatus, MatrixStatus, Period, Tone,
} from '@/mock/cockpit'

/** 较上期变化:箭头文字 + 好坏着色(绿向好 / 红需关注 / 灰持平) */
export function deltaOf(delta: number, goodDir: number) {
  const good = goodDir === 0 ? null : (delta > 0) === (goodDir > 0)
  const flat = good == null || delta === 0
  return {
    d: delta === 0 ? '持平' : (delta > 0 ? '▲ ' : '▼ ') + Math.abs(delta),
    dc: flat ? '#9FB2D1' : good ? '#3FD1A0' : '#FF6B5E',
    st: flat ? '' : good ? '向好' : '需关注',
  }
}

/* ------------------------------------------------------------------ palette */
/** Literal cockpit palette (dark big-screen); brand blue stays on tokens. */
export const K = {
  sky: 'rgb(var(--ck-acc))',
  skyLite: 'rgb(var(--ck-accl))',
  red: '#FF6B5E',
  redHot: '#FF5A4E',
  redSoft: '#FF8A7E',
  redInk: '#FFB2AA',
  green: '#3FD1A0',
  greenInk: '#9FE3C7',
  amber: '#F5B74E',
  amberHot: '#F5A524',
  ink: '#E6EEF9',
  inkHi: '#F4F8FF',
  ink2: '#C9D6EA',
  ink3: '#9FB2D1',
  ink4: '#6F84A6',
  muted: '#4E5F7E',
} as const

/** 深空蓝 / 墨黑 / 政务蓝 */
export const SCR_BG = ['#040A16', '#0A0B0D', '#0B2A66'] as const
export const SCR_GLOW = [
  'radial-gradient(ellipse 60% 40% at 50% 0%,rgba(30,91,216,.28),transparent 70%)',
  'radial-gradient(ellipse 60% 40% at 50% 0%,rgba(245,183,78,.12),transparent 70%)',
  'radial-gradient(ellipse 70% 50% at 50% 0%,rgba(127,211,255,.22),transparent 70%)',
] as const
/**
 * 大屏配色(外观配置 · 大屏配色)落到 CSS 变量:强调色 / 浅强调色 / 描边 / 面板底 / 深底,均为 RGB 三元组,
 * 组件里以 rgb(var(--ck-acc)/.2) 取用,切换配色时整屏面板与强调色一起变。
 */
export const SCR_VARS = [
  { '--ck-acc': '58 160 255', '--ck-accl': '127 195 255', '--ck-line': '90 150 255', '--ck-panel': '10 22 46', '--ck-deep': '4 10 22' },
  { '--ck-acc': '245 183 78', '--ck-accl': '255 214 150', '--ck-line': '170 170 180', '--ck-panel': '22 23 26', '--ck-deep': '6 6 8' },
  { '--ck-acc': '127 211 255', '--ck-accl': '190 232 255', '--ck-line': '140 200 255', '--ck-panel': '14 50 118', '--ck-deep': '6 28 74' },
] as const

/** colour for a total fund difference (city view) */
export const tcolD = (t: number) =>
  t > 1e6 ? '#FF5A4E' : t > 4e5 ? '#FF8A4C' : t > 1e5 ? '#F5B74E' : t >= -1e5 ? '#6F84A6' : t >= -5e5 ? '#4CC9A0' : '#2ED18A'
/** colour for a per-case difference: deficit red / surplus green */
export const dcolD = (v: number) => (v > 0 ? K.red : K.green)

export const ALERT_C: Record<string, string> = {
  提醒函: '#FF8A4C', 预警: '#FF6B5E', 关注: '#F5B74E', 逾期: '#FF8A4C', 意见: '#B9A2FF', 待签收: 'rgb(var(--ck-acc))', 核对: '#B9A2FF', 答复: '#3FD1A0',
}
export const alertColor = (t: string) => ALERT_C[t] ?? 'rgb(var(--ck-accl))'
/** 实时提醒的级别:数字越小越靠前;0–1 为高级别(加强显示) */
export const ALERT_RANK: Record<string, number> = { 预警: 0, 提醒函: 1, 逾期: 1, 关注: 2, 核对: 3, 待签收: 4, 意见: 5, 答复: 6 }
export const TONE_C: Record<Tone, string> = { ok: K.green, warn: K.amber, bad: K.red }
export const LOOP_C: Record<LoopStatus, string> = { ok: K.green, warn: K.amber, act: K.sky }
const MCS: Record<MatrixStatus, [string, string, (v?: number) => string]> = {
  ok: ['rgba(46,209,138,.13)', '#5BE0A6', v => v + '%'],
  low: ['rgba(245,183,78,.15)', '#F5B74E', v => '低 ' + v + '%'],
  np: ['rgba(255,90,78,.17)', '#FF7A6E', () => '未公开'],
  cmt: ['rgba(160,130,255,.17)', '#B9A2FF', v => v + ' 条未复'],
  int: ['rgba(255,255,255,.04)', '#4E5F7E', () => '仅内部'],
  na: ['transparent', '#34445F', () => '—'],
}

export const VIEW_NAME: Record<CockpitView, string> = {
  bub: '病组全景', inst: '机构矩阵', flow: '异地流向', pub: '公开矩阵', dept: '科室矩阵', peer: '同级对标',
}
/** 双屏副屏上已有的视图:医保局 → 公开矩阵、异地流向;本院 → 同级对标、科室矩阵 */
export const SCREEN2_VIEWS: Partial<Record<IdentityId, CockpitView[]>> = { conv: ['pub', 'flow'], hosp: ['peer', 'dept'] }
export type Lens = 'money' | 'eff' | 'err'
export const LENSES: [Lens, string][] = [['money', '钱'], ['eff', '效 · 时间消耗'], ['err', '错 · 审核']]
export type { Period } from '@/mock/cockpit'
export const EASE = 'cubic-bezier(.2,.8,.2,1)'

/* ------------------------------------------------------------------ types */
export interface BubbleItem {
  k: string
  t: string
  sub: string
  x: number
  y: number
  cost: number
  tot: number
  r: number
  city?: number
  small?: boolean
}
export interface DetailRow { k: string; v: string; c: string }

/** who= query values, as in the prototype's hash (#cockpit / #hosp) */
export const WHO_TO_IDN: Record<string, number> = { cockpit: 0, conv: 0, hosp: 1, org: 1 }
export const IDN_TO_WHO: IdentityId[] = ['conv', 'hosp']

/* ------------------------------------------------------------------ store */
/** 同级分位(与 B1 同口径:按表现排位,越高越好;> 50 即优于同级中位) */
export const goodPct = (p: CockpitPeer) => +p.pct.slice(1)
/** 告警确认的键(与服务端 cockpit_alarm_ack.alarm_key 一致) */
export const alarmKeyOf = (a: { type: string; text: string }) => `${a.type}|${a.text}`
const HIGH_ALERT = /预警|提醒函/

/** 让坐标轴上限落在 1 / 1.2 / 1.5 / 1.6 / 2 / 2.5 / 3 / 4 / 5 / 6 / 8 × 10^k 上 */
export function niceCeil(v: number) {
  if (v <= 0) return 1
  const e = 10 ** Math.floor(Math.log10(v))
  const f = [1, 1.2, 1.5, 1.6, 2, 2.5, 3, 4, 5, 6, 8, 10].find(x => x * e >= v - 1e-9) ?? 10
  return f * e
}

export function createCockpitStore(data: Readonly<Ref<CockpitData>>) {
  const s = reactive({
    idn: 0,
    view: 'bub' as CockpitView,
    lens: 'money' as Lens,
    sel: null as string | null,
    period: '月' as Period,
    now: Date.now(),
    grow: false,
    /** 递增即重播全部入场动画(数字滚动 / 走势描线 / 柱条生长) */
    pulse: 0,
    /** 面板入场(柱条生长 / 指针滑入),只在进入页面、切换身份、切换周期时重播 */
    intro: false,
    rot: false,
    rotT: 0,
    dual: false,
    snd: true,
    alOn: false,
    subOn: false,
    /** 本次会话里刚保存的订阅(服务端已接受),覆盖页面数据里的 savedSubscription,直到下次加载 */
    subSaved: {} as Partial<Record<IdentityId, CockpitSavedSubscription | null>>,
    /** 本次会话里已确认(服务端已接受)的告警键 */
    acked: [] as string[],
  })

  const I = computed<CockpitIdentity>(() => data.value.identities[s.idn] ?? data.value.identities[0]!)
  /** 当前周期口径下的指标卡 / 钱 / 效 / 错(月 = 身份顶层;季 / 年 = periods) */
  const PV = computed<CockpitPeriodView>(() => (s.period === '月' ? I.value : I.value.periods?.[s.period] ?? I.value))
  const periodLabel = computed(() => data.value.periodLabels[s.period])
  /** 本周期包含的月数(颜色阈值按月均折算) */
  const span = computed(() => data.value.periodMonths?.[s.period] ?? 1)
  /** 病组 / 机构 / 科室:按周期取期内数据 */
  const drgs = computed(() => data.value.drgs.map(g => ({ ...g, ...(s.period !== '月' ? g.byPeriod?.[s.period] : undefined) })))
  const insts = computed(() => data.value.institutions.map(x => ({ ...x, ...(s.period !== '月' ? x.byPeriod?.[s.period] : undefined) })))
  const deptsP = computed(() => data.value.depts.map(x => ({ ...x, ...(s.period !== '月' ? x.byPeriod?.[s.period] : undefined) })))

  /** 双屏时,副屏专门展示的视图从主屏的视图切换 / 轮播中移除,两块屏不重复 */
  const views = computed<CockpitView[]>(() => {
    const own = SCREEN2_VIEWS[I.value.id] ?? []
    return s.dual ? I.value.views.filter(v => !own.includes(v)) : I.value.views
  })
  const view = computed<CockpitView>(() => (views.value.includes(s.view) ? s.view : 'bub'))
  const lens = computed(() => s.lens)
  const rotN = computed(() => Math.max(3, appearance.rot || 20))
  const scr = computed(() => Math.min(2, Math.max(0, appearance.scr | 0)))
  const motion = computed(() => appearance.mot !== 0)

  let growT: ReturnType<typeof setTimeout> | undefined
  function replay() {
    clearTimeout(growT)
    growT = setTimeout(() => (s.grow = true), 60)
  }
  /** state change that replays the enter animation (bubbles from the centre line etc.) */
  function go2(patch: Partial<typeof s>) {
    Object.assign(s, patch, { grow: false })
    if ('idn' in patch || 'period' in patch) restart()
    replay()
  }
  let introT: ReturnType<typeof setTimeout> | undefined
  /** 重播面板入场动画与数字滚动 */
  function restart() {
    s.pulse++
    s.intro = false
    clearTimeout(introT)
    introT = setTimeout(() => (s.intro = true), 60)
  }
  function dispose() { clearTimeout(growT); clearTimeout(introT) }

  /* ---------- bubble data */
  const bubble = computed(() => {
    const d = data.value
    const id = I.value.id
    const H = id === 'hosp'
    const gray: { x: number; y: number; r: number }[] = []
    let items: BubbleItem[]
    const sub0 = (H ? '本院' : '全市 · 全部机构') + ' · ' + periodLabel.value
    if (!H) {
      items = drgs.value.map(g => ({
        k: g.code, t: g.code + ' ' + g.name, sub: sub0, x: g.cases, y: g.diff, cost: g.cost,
        tot: g.cases * g.diff, r: 6 + Math.sqrt(g.cost) / 17,
      }))
    } else {
      // 灰点:全市同组参照;彩点:本院病组(与 B1 病组表同源)
      drgs.value.forEach(g => gray.push({ x: g.cases * 0.22, y: g.diff, r: 5 + Math.sqrt(g.cost) / 22 }))
      const city = new Map(drgs.value.map(g => [g.code, g]))
      items = (I.value.ownDrgs ?? []).map(o => {
        const g = city.get(o.code)
        const p = { ...o, ...(s.period !== '月' ? o.byPeriod?.[s.period] : undefined) }
        const small = p.cases < 30 * span.value
        return {
          k: o.code, t: o.code + ' ' + (g?.name ?? ''), sub: sub0 + (small ? ' · 病例 < 30/月,门户中并入其他' : ''),
          x: p.cases, y: p.diff, cost: o.cost, tot: p.cases * p.diff, r: 5 + Math.sqrt(o.cost) / 22, city: g?.diff, small,
        }
      })
    }
    // 坐标上限随数据(与周期)走:留出余量,最高的气泡不会顶到象限标签
    const xMax = niceCeil(Math.max(...items.map(i => i.x), ...gray.map(q => q.x), 1) * 1.08)
    const yMax = niceCeil(Math.max(...items.map(i => Math.abs(i.y)), 1) * 1.3)
    const pos = (x: number, y: number) => ({
      x: (Math.min(x / xMax, 1) * 100).toFixed(2),
      y: (50 - Math.max(-1, Math.min(1, y / yMax)) * 50).toFixed(2),
    })
    const flag = lens.value === 'err' ? d.errFlag : lens.value === 'eff' ? d.effFlag : []
    const rc = lens.value === 'err' ? '#FF5A4E' : '#F5A524'
    const xs = items.map(i => i.x).sort((a, b) => a - b)
    const med = xs[Math.floor(xs.length / 2)] ?? 0
    const big = [...items].sort((a, b) => Math.abs(b.tot) - Math.abs(a.tot)).slice(0, H ? 3 : 7).map(i => i.k)
    const g = s.grow
    const sel = s.sel
    const fillOf = (i: BubbleItem) => (i.small ? K.muted : !H ? tcolD(i.tot / span.value) : dcolD(i.y))

    // 小气泡压在大气泡之上(按半径细分层级),重叠时也能看到并点中小的那个
    const main = items.map((i, ix) => {
      const p0 = pos(i.x, i.y)
      const p = g ? p0 : { x: p0.x, y: '50.00' }
      const on = i.k === sel
      const fill = fillOf(i)
      const dl = ix * 35 + 'ms'
      const dd = g ? Math.round(i.r * 2) : 0
      return {
        k: i.k,
        tip: i.t,
        cx: +p0.x, cy: +p0.y, r: i.r,
        style: {
          left: p.x + '%', top: p.y + '%', width: dd + 'px', height: dd + 'px',
          marginLeft: -Math.round(dd / 2) + 'px', marginTop: -Math.round(dd / 2) + 'px',
          background: fill, opacity: g ? 0.9 : 0,
          boxShadow: on ? `0 0 0 2px #fff, 0 0 28px ${fill}` : flag.includes(i.k) ? `0 0 0 3px rgb(var(--ck-deep)), 0 0 0 5px ${rc}, 0 0 18px ${rc}` : `0 0 0 1px rgb(var(--ck-deep)/.55), 0 0 14px color-mix(in srgb,${fill} 33%,transparent)`,
          zIndex: on ? 60 : 10 + Math.max(0, Math.round(40 - i.r)),
          transition: `left .9s ${EASE} ${dl},top 1.1s ${EASE} ${dl},width .8s ${EASE} ${dl},height .8s ${EASE} ${dl},margin .8s ${EASE} ${dl},opacity .5s ease ${dl},box-shadow .3s ease`,
        },
      }
    })
    const pulseK = !H ? items.filter(i => i.tot / span.value > 1e6).map(i => i.k) : big.slice(0, 3)
    const pulses = items.filter(i => pulseK.includes(i.k) || i.k === sel).map((i, j) => {
      const p = pos(i.x, i.y)
      const dd = Math.round(i.r * 2)
      const c = i.k === sel ? '#fff' : fillOf({ ...i, small: false })
      return {
        k: i.k,
        style: {
          left: p.x + '%', top: p.y + '%', width: dd + 'px', height: dd + 'px',
          marginLeft: -dd / 2 + 'px', marginTop: -dd / 2 + 'px', border: '2px solid ' + c,
          animation: `cockPulse 2.6s ease-out ${(j * 0.45).toFixed(2)}s infinite`,
          opacity: g ? undefined : 0,
        },
      }
    })
    /* 标签只给「高量 · 逆差」象限差额最大的前 5 个病组(加上选中项);其余悬停时再显示 */
    const hot = items.filter(i => i.x >= med && i.y > 0 && !i.small).sort((x, y) => y.tot - x.tot).slice(0, 5).map(i => i.k)
    const labelFor = (i: BubbleItem, hover = false) => {
      const p = pos(i.x, i.y)
      const left = +p.x > 66
      const name = (i.t.split(' ')[1] ?? '').split(',')[0]
      return {
        k: i.k,
        t: !H ? i.k + ' ' + name : '本院 ' + i.k,
        style: {
          left: p.x + '%', top: p.y + '%',
          marginLeft: (left ? -(i.r + 8) : i.r + 8) + 'px',
          marginTop: '-12px',
          transform: left ? 'translateX(-100%)' : 'none',
          opacity: g || hover ? 1 : 0,
          transition: hover ? 'none' : 'opacity .6s ease 1s',
        },
      }
    }
    const labels = items
      .filter(i => hot.includes(i.k) || i.k === sel)
      .sort((a, b) => b.y - a.y)
      .map(i => ({ i, l: labelFor(i) }))
    // 标签避让:按绘图区的近似像素尺寸算出每个标签的框,与已放置的标签相交就换到气泡另一侧或往下错开一行
    const PW = 832
    const PH = 430
    const placed: { x0: number; x1: number; y0: number; y1: number }[] = []
    labels.forEach(x => {
      const cx = (Math.min(x.i.x / xMax, 1)) * PW
      const cy = (0.5 - Math.max(-1, Math.min(1, x.i.y / yMax)) * 0.5) * PH
      const w = x.l.t.length * 15 + 18
      const boxOf = (left: boolean, dy: number) => {
        const x0 = left ? cx - x.i.r - 8 - w : cx + x.i.r + 8
        return { x0, x1: x0 + w, y0: cy - 12 + dy, y1: cy + 12 + dy }
      }
      const clash = (b: { x0: number; x1: number; y0: number; y1: number }) =>
        placed.some(q => b.x0 < q.x1 && q.x0 < b.x1 && b.y0 < q.y1 && q.y0 < b.y1)
      const pref = x.l.style.transform !== 'none'
      let best = { left: pref, dy: 0 }
      search: for (const dy of [0, 24, -24, 48, -48]) {
        for (const left of [pref, !pref]) {
          if (!clash(boxOf(left, dy)) && boxOf(left, dy).x0 >= 0 && boxOf(left, dy).x1 <= PW) { best = { left, dy }; break search }
        }
      }
      placed.push(boxOf(best.left, best.dy))
      x.l.style.marginLeft = (best.left ? -(x.i.r + 8) : x.i.r + 8) + 'px'
      x.l.style.transform = best.left ? 'translateX(-100%)' : 'none'
      x.l.style.marginTop = -12 + best.dy + 'px'
    })
    const labelOf = (k: string) => {
      const i = items.find(x => x.k === k)
      return i && !hot.includes(k) && k !== sel ? labelFor(i, true) : null
    }
    const grays = gray.map(q => {
      const p = pos(q.x, q.y)
      const dd = Math.round(q.r * 2)
      return { left: p.x + '%', top: p.y + '%', width: dd + 'px', height: dd + 'px', marginLeft: -Math.round(q.r) + 'px', marginTop: -Math.round(q.r) + 'px' }
    })
    const yT = [1, 0.5, 0, -0.5, -1].map(f => ({ p: (50 - f * 50).toFixed(1), label: f === 0 ? '0' : sign(Math.round(f * yMax)) }))
    const xT = [0, 1, 2, 3, 4].map(i => ({ p: i * 25, label: fmt((xMax / 4) * i) }))
    return { xMax, yMax, items, main, pulses, labels: labels.map(x => x.l), labelOf, grays, yT, xT, med: ((med / xMax) * 100).toFixed(1) }
  })

  /* ---------- KPI ribbon */
  const DELTA_WORD: Record<Period, string> = { 月: '较上月', 季: '较上季', 年: '较去年同期' }
  const deltaWord = computed(() => DELTA_WORD[s.period])
  const ribbon = computed(() =>
    PV.value.kpis.map((k, i) => {
      const { d, dc, st } = deltaOf(k.delta, k.goodDir)
      return {
        k: k.label, v: k.value, u: k.unit,
        d: k.delta === 0 || !k.deltaUnit ? d : d + k.deltaUnit, dc, st,
        first: i === 0,
        // 没有走势数据就不画走势线(不编造)
        trend: k.trend && k.trend.length >= 2 ? k.trend : null,
      }
    }),
  )

  const labelOf = (k: string) => bubble.value.labelOf(k)

  /* ---------- money bars:与指标卡同源(见 mock 中的 bars()) */
  const months = computed(() => {
    const b = PV.value.money.bars
    const H = I.value.id === 'hosp'
    const MS = b.values
    const LS = b.line
    const mx = Math.max(...MS, ...LS) * 1.03
    // 本院月度 / 季度柱从低位起画(差异才看得出);累计柱从 0 起
    const b0 = H && !b.cumulative ? Math.floor(Math.min(...MS, ...LS) * 0.9) : 0
    const last = MS.length - 1
    // 医保局:支出超过预算线;本院:记账与 DRG 支付的缺口高于本口径平均(本院每期都有缺口,全标红就没有重点了)
    const gaps = H ? MS.map((v, i) => (v - LS[i]!) / v) : []
    const gapAvg = gaps.length ? gaps.reduce((a, x) => a + x, 0) / gaps.length : 0
    const label = (v: number) => (v < 100 ? v.toFixed(2) : fmt(v))
    return MS.map((v, i) => {
      const line = LS[i]!
      const over = H ? gaps[i]! > gapAvg && !b.cumulative : v > line
      const cur = i === last
      return {
        h: (((v - b0) / (mx - b0)) * 100).toFixed(1) + '%',
        bOff: (((line - b0) / (v - b0)) * 100).toFixed(1) + '%',
        bg: cur ? (over ? K.red : K.sky) : over ? 'rgba(255,107,94,.55)' : 'rgb(var(--ck-acc)/.28)',
        vc: over ? K.redInk : K.skyLite,
        over, cur,
        l: b.labels[i] ?? '',
        yr: i === b.yearAt ? b.year : '',
        v: label(v),
      }
    })
  })

  /* ---------- tornado:左右两侧各自可点 */
  const torn = computed(() => {
    const items = bubble.value.items
    const pos = items.filter(i => i.tot > 0).sort((a, b) => b.tot - a.tot).slice(0, 5)
    const neg = items.filter(i => i.tot < 0).sort((a, b) => a.tot - b.tot).slice(0, 5)
    const tm = Math.max(...[...pos, ...neg].map(i => Math.abs(i.tot)), 1)
    return Array.from({ length: 5 }, (_, j) => {
      const a = neg[j]
      const b = pos[j]
      return {
        lk: a?.k ?? '', la: a ? wan(a.tot) : '',
        rk: b?.k ?? '', ra: b ? wan(b.tot) : '',
        lw: a ? ((Math.abs(a.tot) / tm) * 100).toFixed(0) + '%' : '0',
        rw: b ? ((Math.abs(b.tot) / tm) * 100).toFixed(0) + '%' : '0',
        lon: !!a && a.k === s.sel, ron: !!b && b.k === s.sel,
      }
    })
  })
  /** 点病组(差异条形图等):不在病组视图时切回病组全景,选中对象卡只在病组视图显示病组 */
  function pickDrg(k: string) {
    if (!k) return
    if (view.value === 'bub') s.sel = k
    else go2({ view: 'bub', sel: k, rotT: 0 })
  }

  /* ---------- institutions */
  const instCols = computed(() => {
    const d = data.value
    const open = I.value.id === 'conv'
    return d.districts.map(dist => {
      const list = insts.value.filter(x => x.district === dist).sort((a, b) => a.tier - b.tier || b.diff - a.diff)
      const avg = Math.round(list.reduce((a, x) => a + x.diff, 0) / (list.length || 1))
      return {
        d: dist, n: list.length, avg: '均 ' + sign(avg), ac: dcolD(avg), hc: open ? K.inkHi : K.ink3, open,
        tiles: list.map(x => {
          const a = Math.min(1, Math.abs(x.diff) / 1200)
          return {
            n: x.name, v: sign(x.diff), fg: x.diff > 0 ? K.redInk : K.greenInk,
            bg: x.diff > 0 ? `rgba(255,90,78,${(0.08 + a * 0.42).toFixed(2)})` : `rgba(46,209,138,${(0.06 + a * 0.3).toFixed(2)})`,
            bd: x.name === s.sel ? '#fff' : 'rgba(255,255,255,.06)',
            tip: x.name + ' · ' + (d.tiers[x.tier] ?? '') + ' · 例均 ' + sign(x.diff) + ' 元',
          }
        }),
      }
    })
  })

  /* ---------- departments */
  const depts = computed(() =>
    deptsP.value.map((d, i) => {
      const a = Math.min(1, Math.abs(d.diff) / 1600)
      const tot = d.cases * d.diff
      return {
        n: d.name, c: fmt(d.cases), v: sign(d.diff), cmi: d.cmi.toFixed(2),
        tot: (tot > 0 ? '超支 ' : '结余 ') + (Math.abs(tot) / 10000).toFixed(1) + ' 万',
        fg: d.diff > 0 ? K.redInk : K.greenInk,
        style: {
          background: d.diff > 0 ? `rgba(255,90,78,${(0.08 + a * 0.34).toFixed(2)})` : `rgba(46,209,138,${(0.06 + a * 0.26).toFixed(2)})`,
          borderColor: d.name === s.sel ? '#fff' : 'rgba(255,255,255,.06)',
          opacity: s.grow ? 1 : 0,
          transform: s.grow ? 'none' : 'translateY(10px) scale(.97)',
          transition: `opacity .5s ease ${i * 40}ms,transform .6s ${EASE} ${i * 40}ms`,
        },
      }
    }),
  )

  /* ---------- peers(分位与 B1 同口径:按数值由低到高;好坏按指标方向判断) */
  const peer = computed(() =>
    data.value.peers.map(r => {
      const mn = Math.min(...r.values)
      const mxv = Math.max(...r.values)
      const gp = goodPct(r)
      return {
        n: r.name, v: r.value, p: r.pct,
        dir: r.higherIsBetter ? '数值高为优' : '数值低为优',
        pc: gp > 50 ? K.green : gp < 35 ? K.redSoft : K.amber,
        st: gp > 50 ? '优于中位' : gp < 50 ? '差于中位' : '持平',
        dots: r.values.map((x, i) => {
          let ps = (x - mn) / (mxv - mn || 1)
          if (!r.higherIsBetter) ps = 1 - ps
          const me = i === (r.ownIndex ?? 2)
          return {
            me,
            x: s.grow ? (4 + ps * 92).toFixed(1) + '%' : '50%',
            tr: `left 1s ${EASE} ${i * 60}ms`,
          }
        }),
      }
    }),
  )
  /** 同级对标汇总:与明细同源,优于 + 差于(+ 持平)= 总项数 */
  const peerStats = computed(() => {
    const ps = data.value.peers
    const better = ps.filter(p => goodPct(p) > 50)
    const worse = ps.filter(p => goodPct(p) < 50)
    const watch = [...ps].sort((a, b) => goodPct(a) - goodPct(b))[0]
    const find = (re: RegExp) => ps.find(p => re.test(p.name))
    return {
      n: ps.length, better, worse,
      watch: watch && goodPct(watch) < 50 ? watch.name : '—',
      cmi: find(/^CMI/), diff: find(/例均基金差额/),
    }
  })

  /* ---------- flows */
  const flows = computed(() =>
    data.value.flows.map((f, i) => {
      const y = 16 + i * 72
      const c = f.scope === '省内' ? K.sky : f.scope === '省外' ? K.amberHot : K.ink4
      return {
        nm: f.name, t: f.scope, sh: f.share.toFixed(1), amt: f.amount, c,
        w: Math.max(3, f.share * 0.9).toFixed(1),
        d: `M200 235 C 360 235, 400 ${y + 22}, 556 ${y + 22}`,
        top: y + 'px',
      }
    }),
  )

  /* ---------- publication matrix */
  const matrix = computed(() =>
    data.value.matrix.map(r => ({
      nm: r.name,
      op: r.internal ? 0.55 : 1,
      cells: r.cells.map(c => {
        const x = MCS[c.status]
        return { txt: x[2](c.value), bg: x[0], fg: x[1] }
      }),
    })),
  )
  /** 公开矩阵汇总:全部按「格」从矩阵现算,三项同一口径 */
  const matrixStats = computed(() => {
    const cells = data.value.matrix.filter(r => !r.internal).flatMap(r => r.cells)
    const cmt = cells.filter(c => c.status === 'cmt')
    return {
      unpublished: cells.filter(c => c.status === 'np').length,
      unread: cells.filter(c => c.status === 'low').length,
      unanswered: cmt.length,
      comments: cmt.reduce((a, c) => a + (c.value ?? 0), 0),
    }
  })

  /* ---------- detail bar:只显示与当前视图相关、数据里真实存在的内容 */
  const det = computed<{ t: string; sub: string; rows: DetailRow[] }>(() => {
    const d = data.value
    const id = I.value.id
    const v = view.value
    const pl = periodLabel.value
    if (v === 'inst') {
      const all = insts.value
      const x = all.find(q => q.name === s.sel)
      if (x) {
        const same = all.filter(q => q.tier === x.tier).sort((a, b) => b.diff - a.diff)
        const dist = all.filter(q => q.district === x.district)
        const distAvg = Math.round(dist.reduce((a, q) => a + q.diff, 0) / (dist.length || 1))
        return {
          t: x.name, sub: x.district + ' · ' + (d.tiers[x.tier] ?? '') + ' · ' + pl,
          rows: [
            { k: '例均基金差额', v: sign(x.diff) + ' 元', c: dcolD(x.diff) },
            { k: '同级别逆差排位', v: `第 ${same.indexOf(x) + 1} / ${same.length} 家`, c: K.ink },
            { k: x.district + '机构均值', v: sign(distAvg) + ' 元', c: dcolD(distAvg) },
          ],
        }
      }
      const def = all.filter(q => q.diff > 0)
      const worst = [...all].sort((a, b) => b.diff - a.diff)[0]
      const avg = Math.round(all.reduce((a, q) => a + q.diff, 0) / (all.length || 1))
      return {
        t: '机构矩阵', sub: '点选机构查看详情 · ' + pl,
        rows: [
          { k: '机构数', v: all.length + ' 家', c: K.ink },
          { k: '逆差机构', v: def.length + ' 家', c: K.redSoft },
          { k: '机构均值', v: sign(avg) + ' 元', c: dcolD(avg) },
          ...(worst ? [{ k: '逆差最大', v: worst.name, c: K.redInk }] : []),
        ],
      }
    }
    if (v === 'dept') {
      const all = deptsP.value
      const x = all.find(q => q.name === s.sel)
      if (x) {
        const tot = x.cases * x.diff
        return {
          t: x.name, sub: '本院科室 · 仅本院可见 · ' + pl,
          rows: [
            { k: '出院病例', v: fmt(x.cases) + ' 例', c: K.ink },
            { k: '例均基金差额', v: sign(x.diff) + ' 元', c: dcolD(x.diff) },
            { k: '科室 CMI', v: x.cmi.toFixed(2), c: K.ink },
            { k: '差额总额', v: (tot > 0 ? '超支 ' : '结余 ') + (Math.abs(tot) / 10000).toFixed(1) + ' 万', c: dcolD(x.diff) },
          ],
        }
      }
      const over = all.filter(q => q.diff > 0)
      const worst = [...all].sort((a, b) => b.cases * b.diff - a.cases * a.diff)[0]
      return {
        t: '科室矩阵', sub: '点选科室查看详情 · ' + pl,
        rows: [
          { k: '科室数', v: all.length + ' 个', c: K.ink },
          { k: '出院病例合计', v: fmt(all.reduce((a, q) => a + q.cases, 0)) + ' 例', c: K.ink },
          { k: '超支科室', v: over.length + ' 个', c: K.redSoft },
          ...(worst && worst.diff > 0 ? [{ k: '超支最多', v: worst.name, c: K.redInk }] : []),
        ],
      }
    }
    if (v === 'peer') {
      const ps = peerStats.value
      return {
        t: '同级对标 · 市三级', sub: '分位越高越好 · 口径 ' + d.periodLabels.月,
        rows: [
          { k: '优于同级中位', v: `${ps.better.length} / ${ps.n} 项`, c: K.green },
          { k: '差于同级中位', v: `${ps.worse.length} / ${ps.n} 项`, c: K.redSoft },
          { k: '最需关注', v: ps.watch, c: K.redSoft },
          ...(ps.cmi ? [{ k: 'CMI 同级分位', v: ps.cmi.pct, c: K.skyLite }] : []),
        ],
      }
    }
    if (v === 'flow') {
      const fs = d.flows
      const share = (sc: string) => fs.filter(f => f.scope === sc).reduce((a, f) => a + f.share, 0).toFixed(1) + '%'
      const top = [...fs].filter(f => f.scope !== '—').sort((a, b) => b.share - a.share)[0]
      return {
        t: '异地流向', sub: d.flowOrigin.name + ' · ' + d.flowOrigin.sub,
        rows: [
          { k: '省内就医占比', v: share('省内'), c: K.skyLite },
          { k: '省外就医占比', v: share('省外'), c: K.amberHot },
          ...(top ? [{ k: '最大流向', v: `${top.name} ${top.share.toFixed(1)}%`, c: K.ink }] : []),
        ],
      }
    }
    if (v === 'pub') {
      const m = matrixStats.value
      return {
        t: '公开矩阵', sub: '指标 × 受众 · 按格计数',
        rows: [
          { k: '应公开未公开', v: m.unpublished + ' 格', c: '#FF8A7E' },
          { k: '发了没人看', v: m.unread + ' 格', c: K.amber },
          { k: '意见集中未答复', v: `${m.unanswered} 格 · ${m.comments} 条`, c: '#B9A2FF' },
        ],
      }
    }
    const items = bubble.value.items
    const it = items.find(i => i.k === s.sel) ?? [...items].sort((a, b) => b.tot - a.tot)[0]
    if (!it) return { t: '—', sub: '', rows: [] }
    return {
      t: it.t, sub: it.sub,
      rows: [
        { k: '病例数', v: fmt(it.x) + ' 例', c: K.ink },
        { k: '例均基金差额', v: sign(it.y) + ' 元', c: dcolD(it.y) },
        id === 'conv'
          ? { k: '次均总费用', v: fmt(it.cost) + ' 元', c: K.ink }
          : { k: '全市同组', v: it.city == null ? '—' : sign(it.city) + ' 元', c: K.ink },
        { k: '差额总额', v: (it.tot > 0 ? '逆差 ' : '结余 ') + wan(it.tot), c: dcolD(it.tot) },
      ],
    }
  })

  /* ---------- right column */
  /** 效:消耗指数看目标带 0.95–1.05(偏高橙 / 偏低绿);CMI 越高越好,不做「偏高」警示 */
  const bullets = computed(() =>
    PV.value.eff.map(e => {
      const p = (Math.max(0, Math.min(1, (e.value - 0.7) / 0.6)) * 100).toFixed(1) + '%'
      if (e.goodDir === 1) {
        const c = e.value >= 0.95 ? K.green : K.amber
        return { k: e.label, v: e.value.toFixed(2), c, st: e.value < 0.95 ? '偏低' : '', p, band: false }
      }
      const c = e.value > 1.05 ? K.amber : e.value < 0.95 ? K.green : K.sky
      const st = e.value > 1.05 ? '偏高' : e.value < 0.95 ? '偏低' : ''
      return { k: e.label, v: e.value.toFixed(2), c, st, p, band: true }
    }),
  )
  const alertsAll = computed(() =>
    I.value.alerts
      .map((a, i) => ({ ty: a.type, c: alertColor(a.type), txt: a.text, t: a.date, hi: ALERT_RANK[a.type] != null && ALERT_RANK[a.type]! <= 1, r: ALERT_RANK[a.type] ?? 9, i }))
      .sort((x, y) => x.r - y.r || x.i - y.i),
  )
  const pipe = computed(() => {
    const L = I.value.loop
    const minPct = Math.min(...L.map(p => p.pct))
    return L.map((p, i) => ({
      n: p.name, v: p.value, pct: p.pct + '%', sub: p.sub, c: LOOP_C[p.status], arrow: i < L.length - 1, act: p.status === 'act',
      lag: p.pct === minPct, overdue: /超期|逾期/.test(p.sub),
      bg: p.status === 'act' ? 'rgb(var(--ck-acc)/.1)' : 'rgba(255,255,255,.025)',
      bd: p.status === 'act' ? 'rgb(var(--ck-acc)/.5)' : 'rgb(var(--ck-line)/.12)',
    }))
  })
  const errs = computed(() =>
    PV.value.errs.map(e => ({ ...e, c: TONE_C[e.tone], ...(e.delta != null ? deltaOf(e.delta, e.goodDir ?? -1) : { d: '', dc: '', st: '' }) })),
  )

  /* ---------- subscription(已保存的状态,来自服务端;本次会话保存后以返回值为准) */
  const savedSub = computed<CockpitSavedSubscription | null>(() => {
    const local = s.subSaved[I.value.id]
    if (local !== undefined) return local
    return I.value.savedSubscription ?? null
  })

  /* ---------- clock & alarm */
  const clock = computed(() => {
    const n = new Date(s.now)
    return {
      time: `${pad(n.getHours())}:${pad(n.getMinutes())}:${pad(n.getSeconds())}`,
      date: `${n.getFullYear()}.${pad(n.getMonth() + 1)}.${pad(n.getDate())} · ${periodLabel.value}`,
    }
  })
  /** 高等级告警:当前身份提醒里的第一条预警 / 提醒函 */
  const alarmSrc = computed(() => I.value.alerts.find(x => HIGH_ALERT.test(x.type)) ?? null)
  const alarm = computed(() => ({
    lv: '高等级',
    ty: alarmSrc.value?.type ?? '',
    t: alarmSrc.value?.text ?? '',
    key: alarmSrc.value ? alarmKeyOf(alarmSrc.value) : '',
    /** 服务端下发的确认状态;undefined = 无服务端状态(离线演示) */
    serverAcked: alarmSrc.value?.acked,
    sub: I.value.alarmSub,
    time: clock.value.time,
  }))

  return {
    s, data, I, PV, periodLabel, span, deltaWord, views, view, lens, rotN, scr, motion, go2, replay, restart, dispose,
    bubble, ribbon, months, torn, pickDrg, insts, deptsP, instCols, depts, peer, peerStats, flows, matrix, matrixStats, det, bullets,
    alertsAll, pipe, errs, savedSub, labelOf, clock, alarm,
  }
}

export type CockpitStore = ReturnType<typeof createCockpitStore>
export const COCKPIT_KEY: InjectionKey<CockpitStore> = Symbol('cockpit')
export function useCockpit(): CockpitStore {
  const st = inject(COCKPIT_KEY)
  if (!st) throw new Error('cockpit store not provided')
  return st
}
