import { computed, inject, reactive, type InjectionKey, type Ref } from 'vue'
import { fmt, hsh, pad, sign, wan } from '@/lib/format'
import { appearance } from '@/app/appearance'
import type { CockpitData, CockpitIdentity, CockpitView, IdentityId, LoopStatus, MatrixStatus, Tone } from '@/mock/cockpit'

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
  sky: '#3AA0FF',
  skyLite: '#7FC3FF',
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

/** colour for a total fund difference (city view) */
export const tcolD = (t: number) =>
  t > 1e6 ? '#FF5A4E' : t > 4e5 ? '#FF8A4C' : t > 1e5 ? '#F5B74E' : t >= -1e5 ? '#6F84A6' : t >= -5e5 ? '#4CC9A0' : '#2ED18A'
/** colour for a per-case difference: deficit red / surplus green */
export const dcolD = (v: number) => (v > 0 ? K.red : K.green)

export const ALERT_C: Record<string, string> = {
  提醒函: '#FF8A4C', 预警: '#FF6B5E', 关注: '#F5B74E', 逾期: '#FF8A4C', 意见: '#B9A2FF', 待签收: '#3AA0FF', 核对: '#B9A2FF', 答复: '#3FD1A0',
}
export const alertColor = (t: string) => ALERT_C[t] ?? '#7FB6FF'
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
export type Period = '月' | '季' | '年'
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
    sfq: 0,
    sct: { 0: true, 1: true } as Record<number, boolean>,
    sch: 0,
    sto: { 0: true, 1: true } as Record<number, boolean>,
  })

  const I = computed<CockpitIdentity>(() => data.value.identities[s.idn] ?? data.value.identities[0]!)
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
    if ('idn' in patch) restart()
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
    const xMax = H ? 500 : 1600
    const yMax = 3200
    const gray: { x: number; y: number; r: number }[] = []
    let items: BubbleItem[]
    if (!H) {
      items = d.drgs.map(g => ({
        k: g.code, t: g.code + ' ' + g.name, sub: '全市 · 全部机构', x: g.cases, y: g.diff, cost: g.cost,
        tot: g.cases * g.diff, r: 6 + Math.sqrt(g.cost) / 17,
      }))
    } else {
      d.drgs.forEach(g => gray.push({ x: g.cases * 0.22, y: g.diff, r: 5 + Math.sqrt(g.cost) / 22 }))
      items = d.drgs.map(g => {
        const k = hsh(g.code)
        const x = g.cases * 0.28 * (0.7 + (k % 60) / 100)
        const y = Math.round(g.diff * (0.6 + (k % 80) / 100) + ((k % 7) - 3) * 60)
        return {
          k: g.code, t: g.code + ' ' + g.name, sub: '本院' + (x < 30 ? ' · 病例 < 30,门户中并入其他' : ''),
          x, y, cost: g.cost, tot: x * y, r: 5 + Math.sqrt(g.cost) / 22, city: g.diff, small: x < 30,
        }
      })
    }
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
    const fillOf = (i: BubbleItem) => (i.small ? K.muted : !H ? tcolD(i.tot) : dcolD(i.y))

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
        style: {
          left: p.x + '%', top: p.y + '%', width: dd + 'px', height: dd + 'px',
          marginLeft: -Math.round(dd / 2) + 'px', marginTop: -Math.round(dd / 2) + 'px',
          background: fill, opacity: g ? 0.9 : 0,
          boxShadow: on ? `0 0 0 2px #fff, 0 0 28px ${fill}` : flag.includes(i.k) ? `0 0 0 3px #040A16, 0 0 0 5px ${rc}, 0 0 18px ${rc}` : `0 0 14px ${fill}55`,
          zIndex: on ? 60 : Math.round(40 - i.r / 4),
          transition: `left .9s ${EASE} ${dl},top 1.1s ${EASE} ${dl},width .8s ${EASE} ${dl},height .8s ${EASE} ${dl},margin .8s ${EASE} ${dl},opacity .5s ease ${dl},box-shadow .3s ease`,
        },
      }
    })
    const pulseK = !H ? items.filter(i => i.tot > 1e6).map(i => i.k) : big.slice(0, 3)
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
    labels.forEach((x, j) => {
      const prev = labels.slice(0, j).filter(p => Math.abs(p.i.x / xMax - x.i.x / xMax) < 0.14 && Math.abs(p.i.y - x.i.y) / yMax < 0.08)
      if (prev.length) x.l.style.marginTop = -12 + prev.length * 24 + 'px'
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
  const ribbon = computed(() =>
    I.value.kpis.map((k, i) => {
      const { d, dc, st } = deltaOf(k.delta, k.goodDir)
      const trend = k.trend && k.trend.length >= 2
        ? k.trend
        : Array.from({ length: 12 }, (_, j) => 10 + (hsh(k.label + j) % 22) + j * 1.2)
      return {
        k: k.label, v: k.value, u: k.unit,
        d, dc, st,
        first: i === 0,
        trend,
      }
    }),
  )

  const labelOf = (k: string) => bubble.value.labelOf(k)

  /* ---------- money bars */
  const months = computed(() => {
    const d = data.value
    const H = I.value.id === 'hosp'
    const MS = H ? d.hospRecorded : d.spend
    const PS = H ? d.hospPaid : null
    const mx = Math.max(...MS, ...(PS ?? [])) * 1.03
    const b0 = PS ? 400 : 0
    const yearAt = d.months.findIndex((m, i) => i > 0 && m === '1')
    const last = MS.length - 1
    // 医保局:支出超过预算线的月份;本院:记账与 DRG 支付的缺口高于近 12 月平均的月份(本院每月都有缺口,全标红就没有重点了)
    const gaps = PS ? MS.map((v, i) => (v - PS[i]!) / v) : []
    const gapAvg = gaps.length ? gaps.reduce((a, x) => a + x, 0) / gaps.length : 0
    return MS.map((v, i) => {
      const line = PS ? PS[i]! : d.budgetLine
      const over = PS ? gaps[i]! > gapAvg : v > line
      const cur = i === last
      return {
        h: (((v - b0) / (mx - b0)) * 100).toFixed(1) + '%',
        bOff: (((line - b0) / (v - b0)) * 100).toFixed(1) + '%',
        bg: cur ? (over ? K.red : K.sky) : over ? 'rgba(255,107,94,.55)' : 'rgba(58,160,255,.28)',
        vc: over ? K.redInk : K.skyLite,
        over, cur,
        l: d.months[i] ?? '',
        yr: i === yearAt ? d.dataAsOf.slice(0, 4) : '',
        v: String(v),
      }
    })
  })

  /* ---------- tornado */
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
        k: (b ?? a)?.k ?? null,
      }
    })
  })

  /* ---------- institutions */
  const instCols = computed(() => {
    const d = data.value
    const open = I.value.id === 'conv'
    return d.districts.map(dist => {
      const list = d.institutions.filter(x => x.district === dist).sort((a, b) => a.tier - b.tier || b.diff - a.diff)
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
    data.value.depts.map((d, i) => {
      const a = Math.min(1, Math.abs(d.diff) / 1600)
      const tot = d.cases * d.diff
      return {
        n: d.name, c: d.cases, v: sign(d.diff), cmi: d.cmi.toFixed(2),
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

  /* ---------- peers */
  const peer = computed(() =>
    data.value.peers.map(r => {
      const mn = Math.min(...r.values)
      const mxv = Math.max(...r.values)
      const pn = +r.pct.slice(1)
      return {
        n: r.name, v: r.value, p: r.pct,
        pc: pn >= 60 ? K.green : pn < 35 ? K.redSoft : K.amber,
        dots: r.values.map((x, i) => {
          let ps = (x - mn) / (mxv - mn || 1)
          if (!r.higherIsBetter) ps = 1 - ps
          const me = i === 2
          return {
            me,
            x: s.grow ? (4 + ps * 92).toFixed(1) + '%' : '50%',
            tr: `left 1s ${EASE} ${i * 60}ms`,
          }
        }),
      }
    }),
  )

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

  /* ---------- detail bar */
  const det = computed<{ t: string; sub: string; rows: DetailRow[] }>(() => {
    const d = data.value
    const id = I.value.id
    const v = view.value
    if (v === 'inst') {
      const x = d.institutions.find(q => q.name === s.sel)
      if (x) {
        const h = hsh(x.name)
        return {
          t: x.name, sub: x.district + ' · ' + (d.tiers[x.tier] ?? ''),
          rows: [
            { k: '例均基金差额', v: sign(x.diff) + ' 元', c: dcolD(x.diff) },
            { k: '同级分位', v: 'P' + (30 + (h % 65)), c: K.ink },
            { k: '清单质控率', v: (92 + (h % 70) / 10).toFixed(1) + '%', c: K.ink },
            { k: '本期签收', v: h % 9 ? '已签收' : '待签收', c: h % 9 ? K.green : K.amber },
          ],
        }
      }
    }
    if (v === 'dept') {
      const x = d.depts.find(q => q.name === s.sel)
      if (x) {
        const tot = x.cases * x.diff
        return {
          t: x.name, sub: '本院科室 · 仅本院可见',
          rows: [
            { k: '出院病例', v: fmt(x.cases) + ' 例', c: K.ink },
            { k: '例均基金差额', v: sign(x.diff) + ' 元', c: dcolD(x.diff) },
            { k: '科室 CMI', v: x.cmi.toFixed(2), c: K.ink },
            { k: '差额总额', v: (tot > 0 ? '超支 ' : '结余 ') + (Math.abs(tot) / 10000).toFixed(1) + ' 万', c: dcolD(x.diff) },
          ],
        }
      }
    }
    if (v === 'peer') {
      const ps = d.peerSummary
      return {
        t: '同级对标 · 市三级', sub: '他院匿名 · 仅显示本院位置',
        rows: [
          { k: '优于中位', v: ps.better, c: K.green },
          { k: '需关注', v: ps.watch, c: K.redSoft },
          { k: 'CMI 分位', v: ps.cmiPct, c: K.skyLite },
          { k: '例均差额分位', v: ps.diffPct, c: K.amber },
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
          : { k: '全市同组', v: sign(it.city ?? 0) + ' 元', c: K.ink },
        { k: '差额总额', v: (it.tot > 0 ? '逆差 ' : '结余 ') + wan(it.tot), c: dcolD(it.tot) },
      ],
    }
  })

  /* ---------- right column */
  const bullets = computed(() =>
    I.value.eff.map(e => {
      const c = e.value > 1.05 ? K.amber : e.value < 0.95 ? K.green : K.sky
      const st = e.value > 1.05 ? '偏高' : e.value < 0.95 ? '偏低' : '正常'
      return { k: e.label, v: e.value.toFixed(2), c, st, p: (Math.max(0, Math.min(1, (e.value - 0.7) / 0.6)) * 100).toFixed(1) + '%' }
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
      bg: p.status === 'act' ? 'rgba(58,160,255,.1)' : 'rgba(255,255,255,.025)',
      bd: p.status === 'act' ? 'rgba(58,160,255,.5)' : 'rgba(90,150,255,.12)',
    }))
  })
  const errs = computed(() =>
    I.value.errs.map(e => ({ ...e, c: TONE_C[e.tone], ...(e.delta != null ? deltaOf(e.delta, e.goodDir ?? -1) : { d: '', dc: '', st: '' }) })),
  )

  /* ---------- clock & alarm */
  const clock = computed(() => {
    const n = new Date(s.now)
    return {
      time: `${pad(n.getHours())}:${pad(n.getMinutes())}:${pad(n.getSeconds())}`,
      date: `${n.getFullYear()}.${pad(n.getMonth() + 1)}.${pad(n.getDate())} · ${data.value.periodLabels[s.period]}`,
    }
  })
  const alarmSrc = computed(() => I.value.alerts.find(x => /预警|提醒函/.test(x.type)) ?? I.value.alerts[0])
  const alarm = computed(() => ({
    lv: '高等级',
    ty: alarmSrc.value?.type ?? '',
    t: alarmSrc.value?.text ?? '',
    sub: I.value.alarmSub,
    time: clock.value.time,
  }))

  return { s, data, I, views, view, lens, rotN, scr, motion, go2, replay, restart, dispose, bubble, ribbon, months, torn, instCols, depts, peer, flows, matrix, det, bullets, alertsAll, pipe, errs, labelOf, clock, alarm }
}

export type CockpitStore = ReturnType<typeof createCockpitStore>
export const COCKPIT_KEY: InjectionKey<CockpitStore> = Symbol('cockpit')
export function useCockpit(): CockpitStore {
  const st = inject(COCKPIT_KEY)
  if (!st) throw new Error('cockpit store not provided')
  return st
}
