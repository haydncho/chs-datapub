import { computed, reactive } from 'vue'
import { authToken } from './session'
import { getJson } from '@/api/client'

/**
 * 外观配置 (A15). Saved settings apply platform-wide via data-* attributes on
 * <html> (see style.css). Same storage key and shape as the prototype's
 * appearance.js so the two stay compatible.
 */
export interface Appearance {
  /** theme colour: 0 政务蓝 1 医保青 2 深海蓝 3 中国红 4 墨绿 */
  c: number
  /** density 0 紧凑 1 标准 2 宽松 */
  dens: number
  /** radius 0 小 1 标准 2 大 */
  rad: number
  /** body font 0 小 1 标准 2 大 */
  font: number
  /** card style 0 描边 1 渐变 2 投影 */
  card: number
  /** cockpit palette 0 深空蓝 1 墨黑 2 政务蓝 */
  scr: number
  /** motion 0 关闭 1 标准 2 强 */
  mot: number
  /** cockpit rotation interval (s) */
  rot: number
  /** watermark 0 浅 1 标准 2 深 */
  wm: number
  name: string
  /** 自定义主题色 `#RRGGBB`;非空时覆盖 `c`(旧数据没有此字段) */
  custom?: string
  /** 菜单风格 0 标准 1 紧凑(写入 data-menu,供壳层使用) */
  menu?: number
  /** 表格斑马纹 0 关 1 开(写入 data-zebra) */
  zebra?: number
}

export const APPEARANCE_KEY = 'yb-appearance'
export const THEME_IDS = ['', 'teal', 'navy', 'red', 'green'] as const
export const THEME_COLORS = ['#1E5BD8', '#0E8A8A', '#16408F', '#C8372D', '#2F7A4F'] as const
/** soft tints per theme — same values as style.css html[data-theme] --brand-soft */
export const THEME_SOFT = ['#EBF1FD', '#E3F4F4', '#E8EEF9', '#FBECEA', '#E8F3EC'] as const

export const DEFAULT_APPEARANCE: Appearance = {
  c: 0, dens: 1, rad: 1, font: 1, card: 1, scr: 0, mot: 1, rot: 20, wm: 1, name: '医保数据公开 · 定向发布平台',
  custom: '', menu: 0, zebra: 0,
}

/* ───────── 主题色:预设表 / 自定义色推导 / 对比度 ───────── */

/** 主题色派生出的全部 CSS 变量(与 style.css 里 html[data-theme] 的值一一对应) */
export const BRAND_VAR_KEYS = [
  '--brand', '--brand-hover', '--brand-soft', '--brand-line', '--brand-tint', '--brand-mute',
  '--primary', '--ring', '--accent', '--accent-foreground',
] as const

/** 预设色的 brand-hover / line / tint / mute(brand、soft 见 THEME_COLORS / THEME_SOFT) */
const THEME_EXTRA = [
  { hover: '#1747AE', line: '#B9CDF6', tint: '#F6F9FF', mute: '#A9BCE8' },
  { hover: '#0B6F6F', line: '#9ED6D6', tint: '#F2FAFA', mute: '#8FC7C7' },
  { hover: '#103273', line: '#AFC0E4', tint: '#F4F7FC', mute: '#9AAED8' },
  { hover: '#A62B23', line: '#EDB9B4', tint: '#FDF6F5', mute: '#E3A39D' },
  { hover: '#24603E', line: '#AFD3BD', tint: '#F4FAF6', mute: '#9CC5AC' },
] as const

/** 最低对比度:主色上叠白色文字(WCAG AA 正文) */
export const MIN_CONTRAST = 4.5

const HEX6 = /^#[0-9a-fA-F]{6}$/

/** 规范化色值:接受 `#abc` / `#aabbcc` / `aabbcc`,返回大写 `#RRGGBB`,非法返回 '' */
export function normalizeHex(v: string | undefined | null): string {
  let t = (v ?? '').trim().replace(/^#/, '')
  if (/^[0-9a-fA-F]{3}$/.test(t)) t = t.split('').map(ch => ch + ch).join('')
  return /^[0-9a-fA-F]{6}$/.test(t) ? `#${t.toUpperCase()}` : ''
}

function rgbOf(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

function hexOf(r: number, g: number, b: number): string {
  return '#' + [r, g, b].map(x => Math.round(Math.min(255, Math.max(0, x))).toString(16).padStart(2, '0')).join('').toUpperCase()
}

/** 向 `to` 混合 t(0–1) */
function mix(hex: string, to: [number, number, number], t: number): string {
  const [r, g, b] = rgbOf(hex)
  return hexOf(r + (to[0] - r) * t, g + (to[1] - g) * t, b + (to[2] - b) * t)
}

function luminance(hex: string): number {
  const f = (c: number) => { const x = c / 255; return x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4 }
  const [r, g, b] = rgbOf(hex)
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
}

/** 色值与白色文字的对比度(1–21) */
export function contrastWithWhite(hex: string): number {
  const h = normalizeHex(hex)
  if (!h) return 0
  return 1.05 / (luminance(h) + 0.05)
}

/** 由主色推导整套层次(混合比例取自 5 个预设色:soft 92% / line 69% / tint 96% / mute 62% 向白,hover 22% 向黑) */
export function deriveBrand(hex: string): Record<(typeof BRAND_VAR_KEYS)[number], string> {
  const base = normalizeHex(hex) || THEME_COLORS[0]
  const W: [number, number, number] = [255, 255, 255]
  const soft = mix(base, W, 0.92)
  return {
    '--brand': base,
    '--brand-hover': mix(base, [0, 0, 0], 0.22),
    '--brand-soft': soft,
    '--brand-line': mix(base, W, 0.69),
    '--brand-tint': mix(base, W, 0.96),
    '--brand-mute': mix(base, W, 0.62),
    '--primary': base,
    '--ring': base,
    '--accent': soft,
    '--accent-foreground': base,
  }
}

/** 某份外观实际生效的主色(自定义色优先) */
export function brandOf(a: Pick<Appearance, 'c' | 'custom'>): string {
  return normalizeHex(a.custom) || THEME_COLORS[a.c] || THEME_COLORS[0]
}

/** 某份外观的主题变量全集——真实页面用 applyAppearance 写到 <html>,A15 预览写到预览容器 */
export function themeVars(a: Pick<Appearance, 'c' | 'custom'>): Record<(typeof BRAND_VAR_KEYS)[number], string> {
  const custom = normalizeHex(a.custom)
  if (custom) return deriveBrand(custom)
  const i = THEME_COLORS[a.c] ? a.c : 0
  const x = THEME_EXTRA[i]!
  return {
    '--brand': THEME_COLORS[i]!, '--brand-hover': x.hover, '--brand-soft': THEME_SOFT[i]!, '--brand-line': x.line,
    '--brand-tint': x.tint, '--brand-mute': x.mute, '--primary': THEME_COLORS[i]!, '--ring': THEME_COLORS[i]!,
    '--accent': THEME_SOFT[i]!, '--accent-foreground': THEME_COLORS[i]!,
  }
}

const clampInt = (v: unknown, min: number, max: number, d: number) => {
  const n = Math.round(Number(v))
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : d
}

/** 清洗任意来源(localStorage / 服务端)的外观数据:补默认值、夹取范围、丢弃非法或对比度不足的自定义色 */
export function normalizeAppearance(raw: Partial<Appearance> | null | undefined): Appearance {
  const r = (raw && typeof raw === 'object' ? raw : {}) as Partial<Appearance>
  const d = DEFAULT_APPEARANCE
  let custom = normalizeHex(r.custom)
  if (custom && contrastWithWhite(custom) < MIN_CONTRAST) custom = ''
  return {
    c: clampInt(r.c, 0, 4, d.c),
    dens: clampInt(r.dens, 0, 2, d.dens),
    rad: clampInt(r.rad, 0, 2, d.rad),
    font: clampInt(r.font, 0, 2, d.font),
    card: clampInt(r.card, 0, 2, d.card),
    scr: clampInt(r.scr, 0, 2, d.scr),
    mot: clampInt(r.mot, 0, 2, d.mot),
    rot: clampInt(r.rot, 3, 600, d.rot),
    wm: clampInt(r.wm, 0, 2, d.wm),
    name: typeof r.name === 'string' && r.name.trim() ? r.name.slice(0, 40) : d.name,
    custom,
    menu: clampInt(r.menu, 0, 1, 0),
    zebra: clampInt(r.zebra, 0, 1, 0),
  }
}

function load(): Appearance {
  try {
    const raw = localStorage.getItem(APPEARANCE_KEY)
    if (raw) return normalizeAppearance(JSON.parse(raw))
  } catch { /* storage unavailable */ }
  return { ...DEFAULT_APPEARANCE }
}

/** The published (saved) appearance. A15 edits a draft copy and calls `publishAppearance`. */
export const appearance = reactive<Appearance>(load())

/** 平台名称拆成顶栏两行:「主名 · 副名」→ 主名 / 副名;无分隔符时只有一行 */
export const platformName = computed(() => {
  const [main = '', ...rest] = appearance.name.split(/\s*·\s*/)
  return { main, sub: rest.join(' · '), full: appearance.name }
})

export function applyAppearance(a: Appearance = appearance) {
  const el = document.documentElement
  const custom = normalizeHex(a.custom)
  const theme = custom ? '' : (THEME_IDS[a.c] ?? '')
  if (theme) el.dataset.theme = theme
  else delete el.dataset.theme
  // 自定义主题色:在 <html> 上内联覆盖整套品牌变量;否则清理干净,交还给 style.css 的 data-theme 规则
  if (custom) {
    for (const [k, v] of Object.entries(deriveBrand(custom))) el.style.setProperty(k, v)
  } else {
    for (const k of BRAND_VAR_KEYS) el.style.removeProperty(k)
  }
  el.dataset.dens = String(a.dens)
  el.dataset.card = String(a.card)
  el.dataset.radius = String(a.rad)
  el.dataset.font = String(a.font)
  el.dataset.wm = String(a.wm)
  el.dataset.motion = String(a.mot)
  el.dataset.menu = String(a.menu ?? 0)
  el.dataset.zebra = String(a.zebra ?? 0)
}

export function publishAppearance(next: Appearance) {
  const clean = normalizeAppearance(next)
  Object.assign(appearance, clean)
  try { localStorage.setItem(APPEARANCE_KEY, JSON.stringify(clean)) } catch { /* ignore */ }
  applyAppearance()
}

/**
 * 从服务端拉取"平台级"外观(GET /api/v1/settings/appearance)并生效。服务端是唯一来源:
 * 空对象 = 平台默认外观(从未设置,或 A15「恢复默认」),本地缓存的旧外观随之清除。
 * 请求失败(未启动后端 / 未登录)静默,保留本地缓存;建议在应用启动和登录成功后各调用一次。
 */
export async function syncAppearanceFromServer(): Promise<void> {
  if (!authToken()) return // the setting is only served to signed-in users
  try {
    const remote = await getJson<Partial<Appearance>>('/settings/appearance')
    if (!remote || typeof remote !== 'object') return
    if (Object.keys(remote).length === 0) {
      resetAppearance()
      return
    }
    const next = normalizeAppearance(remote)
    Object.assign(appearance, next)
    try { localStorage.setItem(APPEARANCE_KEY, JSON.stringify(next)) } catch { /* ignore */ }
    applyAppearance()
  } catch { /* 静默:保留本地外观 */ }
}

export function resetAppearance() {
  try { localStorage.removeItem(APPEARANCE_KEY) } catch { /* ignore */ }
  Object.assign(appearance, DEFAULT_APPEARANCE)
  applyAppearance()
}

window.addEventListener('storage', e => {
  if (e.key === APPEARANCE_KEY) {
    Object.assign(appearance, load())
    applyAppearance()
  }
})
