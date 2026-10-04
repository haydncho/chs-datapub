/** Number / text helpers shared by every page (ported from the prototypes). */

/** 1234.5 → "1,235" */
export const fmt = (n: number) => Math.round(n).toLocaleString('zh-CN')

/** signed with a real minus sign: +1,860 / −420 */
export const sign = (n: number) => (n > 0 ? '+' : n < 0 ? '−' : '') + fmt(Math.abs(n))

/** 1860000 → "186万" */
export const wan = (n: number) => (Math.abs(n) / 10000).toFixed(0) + '万'

export const pad = (n: number) => String(n).padStart(2, '0')

/** Deterministic small hash used to derive stable demo variations from a code (e.g. "BR25"). */
export const hsh = (s: string) => {
  let x = 7
  for (const c of s) x = (x * 31 + c.charCodeAt(0)) % 9973
  return x
}

/**
 * Split a KPI string into number and unit so the unit can be rendered smaller:
 * "1,284 万" → { vn: "1,284", vu: "万" }; "94.6" → { vn: "94.6", vu: "" }.
 */
export const splitUnit = (v: string | number) => {
  const s = String(v)
  const i = s.lastIndexOf(' ')
  if (i > 0 && !/[\d%]/.test(s.slice(i + 1))) return { vn: s.slice(0, i), vu: s.slice(i + 1) }
  return { vn: s, vu: '' }
}

/** yyyy-mm-dd hh:mm for watermarks / stamps */
export const stamp = (d = new Date()) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
