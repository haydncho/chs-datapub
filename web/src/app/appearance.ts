import { reactive } from 'vue'

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
}

export const APPEARANCE_KEY = 'yb-appearance'
export const THEME_IDS = ['', 'teal', 'navy', 'red', 'green'] as const
export const THEME_COLORS = ['#1E5BD8', '#0E8A8A', '#16408F', '#C8372D', '#2F7A4F'] as const
/** soft tints per theme — same values as style.css html[data-theme] --brand-soft */
export const THEME_SOFT = ['#EBF1FD', '#E3F4F4', '#E8EEF9', '#FBECEA', '#E8F3EC'] as const

export const DEFAULT_APPEARANCE: Appearance = {
  c: 0, dens: 1, rad: 1, font: 1, card: 1, scr: 0, mot: 1, rot: 20, wm: 1, name: '医保数据公开 · 定向发布平台',
}

function load(): Appearance {
  try {
    const raw = localStorage.getItem(APPEARANCE_KEY)
    if (raw) return { ...DEFAULT_APPEARANCE, ...JSON.parse(raw) }
  } catch { /* storage unavailable */ }
  return { ...DEFAULT_APPEARANCE }
}

/** The published (saved) appearance. A15 edits a draft copy and calls `publishAppearance`. */
export const appearance = reactive<Appearance>(load())

export function applyAppearance(a: Appearance = appearance) {
  const el = document.documentElement
  const theme = THEME_IDS[a.c] ?? ''
  if (theme) el.dataset.theme = theme
  else delete el.dataset.theme
  el.dataset.dens = String(a.dens)
  el.dataset.card = String(a.card)
  el.dataset.radius = String(a.rad)
  el.dataset.font = String(a.font)
  el.dataset.wm = String(a.wm)
  el.dataset.motion = String(a.mot)
}

export function publishAppearance(next: Appearance) {
  Object.assign(appearance, next)
  try { localStorage.setItem(APPEARANCE_KEY, JSON.stringify(next)) } catch { /* ignore */ }
  applyAppearance()
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
