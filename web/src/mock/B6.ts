/** B6 政策培训 — seed data (same shape as GET /api/v1/pages/B6). */

export type B6Tone = 'brand' | 'warn' | 'ok'
export type B6Icon = 'book' | 'chart' | 'shield' | 'chat'

export interface B6Course {
  id: string
  tag: string
  name: string
  desc: string
  minutes: number
  tone: B6Tone
  icon: B6Icon
  done: boolean
  /** quiz score (shown when done) */
  score: number
}

export type B6DocKind = '国家' | '本市' | '口径'

export interface B6Doc {
  kind: B6DocKind
  name: string
  /** document number (文号) */
  number: string
  date: string
}

export interface B6Data {
  subtitle: string
  courses: B6Course[]
  docs: B6Doc[]
}

export const B6_SEED: B6Data = {
  subtitle: '读懂报告 · 理解口径 · 本院 18 人已完成必修',
  courses: [
    { id: 'C1', tag: '必修', name: '读懂 DRG 月度报告', desc: '从例均基金差额到偏离贡献,10 个关键指标', minutes: 18, tone: 'brand', icon: 'book', done: true, score: 92 },
    { id: 'C2', tag: '必修', name: '对标档位与分位', desc: '匿名分位、匿名编号与具名的区别', minutes: 12, tone: 'brand', icon: 'chart', done: true, score: 88 },
    { id: 'C3', tag: '必修', name: '结算清单质控要点', desc: 'R-117 等高频规则逐条讲解', minutes: 25, tone: 'warn', icon: 'shield', done: false, score: 95 },
    { id: 'C4', tag: '必修', name: '意见与申诉流程', desc: '如何提意见、时限与答复', minutes: 8, tone: 'ok', icon: 'chat', done: false, score: 95 },
  ],
  docs: [
    { kind: '国家', name: 'DRG/DIP 支付方式改革三年行动计划', number: '医保发〔2021〕48号', date: '2021-11' },
    { kind: '国家', name: '医保基金使用监督管理条例', number: '国务院令第735号', date: '2021-02' },
    { kind: '本市', name: '示例市 DRG 付费实施细则(2026 版)', number: '示医保发〔2026〕3号', date: '2026-01' },
    { kind: '本市', name: '医保数据定向公开管理办法(试行)', number: '示医保发〔2026〕11号', date: '2026-04' },
    { kind: '口径', name: '指标口径手册 v2.1', number: '—', date: '2026-07' },
  ],
}

/**
 * ACTIONS:
 * completeCourse({ id: string }) — 开始学习: marks the required course as completed for the current user (demo: immediately, with the course's quiz score).
 */
