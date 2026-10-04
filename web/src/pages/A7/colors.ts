/** Chart palette for the A7 manuscript (brand-led blue ramp + neutrals). */
import { BRAND } from '@/lib/palette'

/** 药品 / 耗材 / 检查检验 / 治疗 / 护理及其他 — also used for the tier bar. */
export const RAMP = [BRAND, '#5B8FD9', '#9DBCE9', '#6B778C', '#C9D3E1'] as const
/** readable label colour on each RAMP step */
export const RAMP_INK = ['#fff', '#fff', '#0F1A2E', '#fff', '#0F1A2E'] as const
/** tier bar: 市三级 / 县三级 / 二级 / 一级 */
export const TIER_C = [BRAND, '#5B8FD9', '#9DBCE9', '#C9D3E1'] as const
export const pctStr = (n: number) => n.toFixed(1) + '%'
