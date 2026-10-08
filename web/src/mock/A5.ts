/** A5 图表与报告模板 — demo data (same shape as GET /api/v1/pages/A5). */

export interface A5Section {
  name: string
  /** bound indicators; '—' = free content (自由内容) */
  indicators: string
  charts: string[]
}

export interface A5Template {
  name: string
  desc: string
  refs: number
  version: number
  sections: A5Section[]
  /** server state: the working copy has changes not yet saved as a new version */
  dirty?: boolean
}

/** accent colour of a thumbnail's highlighted shape */
export type A5Accent = 'brand' | 'ink' | 'warn' | 'bad'

/**
 * Symbolic SVG thumbnail (viewBox 0 0 100 40). Layers, bottom → top:
 * track (light grey fill), glow (brand 12% fill), fill (mid-blue fill),
 * dash (grey dashed stroke), stroke (brand line), accent (accent fill).
 */
export interface A5Thumb {
  track?: string
  glow?: string
  fill?: string
  dash?: string
  stroke?: string
  accent?: string
  accentTone?: A5Accent
}

export interface A5Chart {
  name: string
  desc: string
  thumb: A5Thumb
}

export interface A5Data {
  templates: A5Template[]
  library: A5Chart[]
}

const CIR = (x: number, y: number, r: number) => `M${x - r} ${y}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0`

export const A5_SEED: A5Data = {
  templates: [
    {
      name: 'DRG 月度运行报告', desc: '月告知 · 定点医疗机构', refs: 14, version: 3,
      sections: [
        { name: '本月运行概况', indicators: '例均基金差额 · 记账 vs 支付', charts: ['KPI 卡', '对比条'] },
        { name: '核心指标与同级分位', indicators: 'CMI · 费用/时间消耗指数', charts: ['分位条'] },
        { name: '病组结构与偏离贡献', indicators: '病组差额', charts: ['气泡图', '贡献条'] },
        { name: '质量与监管', indicators: '清单质控率 · 审核扣款', charts: ['趋势线'] },
        { name: '异地就医', indicators: '异地支出占比', charts: ['流向条'] },
        { name: '政策提示与下月关注', indicators: '—', charts: ['文本'] },
      ],
    },
    {
      name: '季度运行分析', desc: '季度 · 县区医保 / 专家组', refs: 4, version: 2,
      sections: [
        { name: '基金运行', indicators: '结余率 · 预算执行', charts: ['趋势线', 'KPI 卡'] },
        { name: '县区对比', indicators: '例均差额', charts: ['对比条'] },
        { name: '病组专题摘要', indicators: '—', charts: ['文本'] },
      ],
    },
    {
      name: '病种专题(七段式)', desc: '专题 · 收治机构', refs: 6, version: 1,
      sections: [
        { name: '整体描述', indicators: '—', charts: ['KPI 卡', '堆叠条'] },
        { name: '费用结构', indicators: '—', charts: ['堆叠条'] },
        { name: '关键行为', indicators: '—', charts: ['点图'] },
        { name: '差异归因', indicators: '—', charts: ['瀑布图'] },
        { name: '标杆对比', indicators: '—', charts: ['哑铃图'] },
        { name: '优化空间', indicators: '—', charts: ['KPI 卡'] },
        { name: '建议', indicators: '—', charts: ['文本'] },
      ],
    },
    {
      name: '预警提醒函', desc: '即时 · 单机构', refs: 22, version: 1,
      sections: [
        { name: '预警事项', indicators: '—', charts: ['KPI 卡'] },
        { name: '趋势与阈值', indicators: '—', charts: ['趋势线'] },
        { name: '回执要求', indicators: '—', charts: ['文本'] },
      ],
    },
  ],
  library: [
    { name: 'KPI 卡', desc: '单值 + 环比', thumb: { track: 'M6 6h30v4H6z M6 32h22v3H6z', accent: 'M6 14h42v13H6z', fill: 'M54 22h8v5h-8z', stroke: 'M72 27l7-9 7 9', accentTone: 'ink' } },
    { name: '分位条', desc: '同级位置', thumb: { track: 'M4 17h92v6H4z', fill: 'M27 17h46v6H27z', dash: 'M50 9v22', accent: 'M70 9h4v22h-4z', accentTone: 'warn' } },
    { name: '趋势线', desc: '12 期', thumb: { dash: 'M2 14h96', glow: 'M2 33L16 27L30 29L44 21L58 23L72 13L86 16L98 8V38H2z', stroke: 'M2 33L16 27L30 29L44 21L58 23L72 13L86 16L98 8', accent: CIR(98, 8, 2.6) } },
    { name: '对比条', desc: '本院 vs 全市', thumb: { accent: 'M4 5h72v6H4z M4 22h52v6H4z', fill: 'M4 12h58v4H4z M4 29h40v4H4z' } },
    { name: '气泡图', desc: '量 × 差额', thumb: { track: 'M50 0h50v20H50z', dash: 'M0 20h100 M50 0v40', fill: CIR(22, 28, 5) + CIR(36, 12, 3.5) + CIR(64, 30, 6) + CIR(86, 26, 3), accent: CIR(76, 10, 7) + CIR(58, 14, 4), accentTone: 'bad' } },
    { name: '堆叠条', desc: '结构占比', thumb: { accent: 'M4 6h32v9H4z M4 25h24v9H4z', fill: 'M37 6h22v9H37z M29 25h32v9H29z', track: 'M60 6h36v9H60z M62 25h34v9H62z' } },
    { name: '瀑布图', desc: '差异分解', thumb: { fill: 'M6 18h16v20H6z M78 4h16v34H78z', accent: 'M30 11h16v7H30z M54 4h16v7H54z', dash: 'M22 18h8 M46 11h8 M70 4h8', accentTone: 'warn' } },
    { name: '哑铃图', desc: '两组对比', thumb: { track: 'M18 7h58v2H18z M28 19h52v2H28z M12 31h44v2H12z', fill: CIR(18, 8, 4) + CIR(28, 20, 4) + CIR(12, 32, 4), accent: CIR(76, 8, 4) + CIR(80, 20, 4) + CIR(56, 32, 4), accentTone: 'bad' } },
  ],
}

/**
 * ACTIONS:
 * addChart({ template: number, section: number, chart: string }) — 将图表组件 chart 加入模板 template(下标)的第 section 章
 *   (同一章节不可重复加入);
 * removeChart({ template: number, section: number, chart: string }) — 从该章节移除图表;
 * saveTemplateVersion({ template: number }) — 将当前模板保存为新版本(服务端在已保存版本上 +1 并返回 version;
 *   没有未保存修改时拒绝),下期起生效。
 * Server state (page_state tpl:<i>) is merged into templates[i]: sections[].charts, version, dirty.
 */
