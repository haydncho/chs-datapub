/**
 * Literal colours for data-driven styling (SVG fills, chart marks, computed
 * :style bindings) where a Tailwind class cannot be used. Prefer the Tailwind
 * tokens (text-ok-ink, bg-warn-soft …) in templates; use these only for
 * values computed in script. Brand-coloured marks should use `BRAND`
 * (a CSS var) so 外观配置 can re-theme them.
 */
export const G = '#0F9960', GS = '#E7F6EF', GT = '#0F7A4D'
export const A = '#E8890C', AS = '#FEF3E2', AT = '#C76E0A'
export const R = '#D2362B', RS = '#FDECEA', RT = '#B42318'
export const V = '#6941C6', VS = '#F2EEFB'
export const BRAND = 'var(--brand)'
export const BRAND_SOFT = 'var(--brand-soft)'
export const BRAND_LINE = 'var(--brand-line)'
export const BRAND_TINT = 'var(--brand-tint)'
export const BRAND_MUTE = 'var(--brand-mute)'
export const INK = ['#0F1A2E', '#2A3650', '#445066', '#6B778C', '#98A2B3', '#C3CAD6'] as const

/** Light-to-dark blue ramp for secondary chart series (A7 / B-pages). */
export const BLUE_RAMP = ['#5B8FD9', '#9DBCE9', '#C9D3E1'] as const
