/** A13 展示策略 — seed data (same shape as GET /api/v1/pages/A13). */

export interface A13NumberRule {
  key: 'minOrg' | 'minCase'
  kind: 'number'
  name: string
  desc: string
  value: number
  unit: string
  min: number
  max: number
  /** −/+ step */
  step: number
}

export interface A13SwitchRule {
  key: 'wm' | 'exp' | 'named'
  kind: 'switch'
  name: string
  desc: string
  value: boolean
}

export type A13Rule = A13NumberRule | A13SwitchRule

export interface A13Preview {
  /** caption of the preview card */
  title: string
  /** peer-group size in the preview (compared with minOrg) */
  peerCount: number
  metric: string
  value: string
  cityMean: string
  percentile: string
  watermark: string
}

export interface A13Data {
  rules: A13Rule[]
  preview: A13Preview
}

export const A13_SEED: A13Data = {
  rules: [
    { key: 'minOrg', kind: 'number', name: '小样本抑制 · 同级机构数', desc: '同级机构少于该值时不输出分位与排名', value: 5, unit: '家', min: 2, max: 10, step: 1 },
    { key: 'minCase', kind: 'number', name: '小样本抑制 · 病组病例数', desc: '病例数少于该值的病组并入“其他”', value: 30, unit: '例', min: 10, max: 100, step: 5 },
    { key: 'wm', kind: 'switch', name: '动态水印', desc: '页面与导出文件叠加姓名、机构、时间', value: true },
    { key: 'exp', kind: 'switch', name: '允许机构导出 PDF', desc: '导出文件带水印与追溯编码', value: true },
    { key: 'named', kind: 'switch', name: '具名排行需逐指标审批', desc: '未审批指标一律按匿名分位发布', value: true },
  ],
  preview: {
    title: '实时效果预览 · 县三级同级组(4 家)',
    peerCount: 4,
    metric: '例均基金差额 · 本院',
    value: '+712 元',
    cityMean: '−38 元',
    percentile: 'P75',
    watermark: '钱丽 甲县医保局 2026-10-04',
  },
}

/**
 * ACTIONS:
 * setPolicyRule({ key: 'minOrg'|'minCase'|'wm'|'exp'|'named', value: number|boolean }) — change one global display rule (−/+ or switch); applies to all publications immediately (召集人 / 行政管理组; validated server-side against min/max/step, saved values come back in GET /pages/A13).
 */
