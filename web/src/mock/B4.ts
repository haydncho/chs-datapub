/** B4 报告中心 — seed data (institution view). Same shape as GET /api/v1/pages/B4. */

export type ReportStatus = 'sign' | 'check' | 'signed' | 'old'

export interface B4Report {
  name: string
  type: string
  /** publish date MM-DD */
  date: string
  pages: string
  status: ReportStatus
  version: string
}

export interface B4CoverKpi {
  label: string
  value: string
  sub: string
  /** 'num' = big Barlow numeral, 'text' = 14px label */
  kind: 'num' | 'text'
  tone?: 'bad'
}

export interface B4Stat { label: string; value: string; tone?: 'bad' }

/** inline text segment for the v1/v2 paragraph comparison */
export interface B4Seg { t: string; mark?: 'del' | 'add' | 'note' }

export interface B4DiffRow { pos: string; item: string; before: string; after: string; why: string }

export interface B4Data {
  reports: B4Report[]
  issuer: string
  audience: string
  coverKpis: B4CoverKpi[]
  toc: { n: string; label: string; page: number }[]
  overview: { page: string; title: string; stats: B4Stat[]; text: string }
  footerNote: string
  signNote: string
  signer: string
  checkNote: string
  correctionNote: string
  diff: {
    from: string
    to: string
    summary: string
    rows: B4DiffRow[]
    section: string
    before: B4Seg[]
    after: B4Seg[]
  }
  readLog: { count: number; last: string }
}

export const B4_SEED: B4Data = {
  reports: [
    { name: '2026年8月 DRG月度运行报告', type: '月度报告', date: '09-12', pages: '18 页', status: 'sign', version: 'v1' },
    { name: 'BR25 脑缺血性疾患专题', type: '专题报告 · 核对稿', date: '09-20', pages: '24 页', status: 'check', version: 'v3' },
    { name: '2026年7月 DRG月度运行报告', type: '月度报告', date: '08-12', pages: '17 页', status: 'signed', version: 'v2' },
    { name: '2026Q2 季度运行分析', type: '季度报告', date: '07-18', pages: '32 页', status: 'signed', version: 'v1' },
    { name: '2026年6月 DRG月度运行报告', type: '月度报告', date: '07-12', pages: '17 页', status: 'old', version: 'v2' },
  ],
  issuer: '示例市医疗保障局 · 医保数据工作组',
  audience: '定向发布 · 示例市第一人民医院',
  coverKpis: [
    { label: '本月偏离', value: '−5.1%', sub: '记账 4,862 万 · 支付 4,616 万', kind: 'num', tone: 'bad' },
    { label: 'CMI 同级分位', value: 'P68', sub: 'CMI 1.12 · 环比 +0.03', kind: 'num' },
    { label: '下月关注', value: 'BR25 · IU29', sub: '合计贡献逆差 61%', kind: 'text' },
  ],
  toc: [
    { n: '一', label: '本月运行概况', page: 2 },
    { n: '二', label: '本院核心指标与同级分位', page: 4 },
    { n: '三', label: '病组结构与偏离贡献', page: 7 },
    { n: '四', label: '质量与监管', page: 11 },
    { n: '五', label: '异地就医', page: 14 },
    { n: '六', label: '政策提示与下月关注', page: 16 },
  ],
  overview: {
    page: '— 2 —',
    title: '一、本月运行概况',
    stats: [
      { label: '医保记账', value: '4,862 万' },
      { label: 'DRG 支付', value: '4,616 万' },
      { label: '偏离', value: '−5.1%', tone: 'bad' },
    ],
    text: '8 月本院 DRG 入组病例 3,412 例,医保记账总额 4,862.4 万元,DRG 支付 4,615.7 万元,偏离 −246.7 万元。逆差主要来自 BR25、IU29 两个病组,合计贡献逆差 61%。CMI 1.12,位于同级 P68。',
  },
  footerNote: '仅限本院内部使用 · 全程留痕',
  signNote: '签收表示本院已收到并阅读本报告,不代表认同报告结论。对内容有异议,可在签收后 10 个工作日内提出意见。',
  signer: '李敏 医保办主任',
  checkNote: '核对稿 · 剩 2 天 · 本院 4 项数据待确认',
  correctionNote: '本报告已发布更正版 v2:§3 GG19 病例数 820 → 796(依据 YJ-0925)。原版本保留可查。',
  diff: {
    from: 'v1 · 07-12 发布',
    to: 'v2 · 08-26 更正',
    summary: '4 处变更 · 依据 YJ-0925',
    rows: [
      { pos: '§3 病组结构', item: 'GG19 本院病例数', before: '820 例', after: '796 例', why: 'YJ-0925 纠错' },
      { pos: '§3 病组结构', item: 'GG19 例均基金差额', before: '+980 元', after: '+1,012 元', why: '随病例数重算' },
      { pos: '§1 运行概况', item: '本院偏离率', before: '−5.3%', after: '−5.1%', why: '汇总重算' },
      { pos: '§6 下月关注', item: '关注病组', before: 'BR25 · IU29', after: 'BR25 · IU29 · GG19', why: '新增编码复核' },
    ],
    section: '§3 病组结构与偏离贡献',
    before: [
      { t: 'GG19 肛门及肛周手术本月入组 ' },
      { t: '820 例', mark: 'del' },
      { t: ',例均基金差额 ' },
      { t: '+980 元', mark: 'del' },
      { t: ',为本院逆差第三大病组。' },
    ],
    after: [
      { t: 'GG19 肛门及肛周手术本月入组 ' },
      { t: '796 例', mark: 'add' },
      { t: ',例均基金差额 ' },
      { t: '+1,012 元', mark: 'add' },
      { t: ',为本院逆差第三大病组。' },
      { t: '(24 例重复上传已剔除)', mark: 'note' },
    ],
  },
  readLog: { count: 6, last: '李敏 10-04 09:12' },
}

/**
 * ACTIONS:
 * signReport({ name: string, version: string, signer: string }) — 签收 a 待签收 report after 已阅读全文; status → 'signed', receipt sent to 示例市医保局;
 * exportReport({ name: string, version: string, format: 'pdf' }) — 打印 / 导出 PDF (watermarked); recorded for the audit trail.
 */
