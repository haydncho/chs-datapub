/** D1 移动端 — seed data (same shape as GET /api/v1/pages/D1). */

/** phone screens of the clickable prototype */
export type D1Screen = 'home' | 'report' | 'alert' | 'rcpt' | 'done' | 'msgs' | 'me'
/** what a todo / message points at: a phone screen, or 'B5' (navigates to 意见核对) */
export type D1Target = 'report' | 'alert' | 'B5'

export interface D1Todo {
  id: 'sign' | 'receipt' | 'verify'
  label: string
  target: D1Target
  /** dot colour key while pending */
  tone: 'warn' | 'bad' | 'violet'
  /** label shown on the right once done */
  doneLabel: string
}

export interface D1Message {
  label: string
  /** time / status text while pending */
  time: string
  /** replaces `time` once the linked task is done ('' = never changes) */
  doneTime: string
  /** linked task: sign → 报告签收, receipt → 提醒函回执; null = plain notice */
  task: 'sign' | 'receipt' | null
  target: D1Target | null
  tone: 'brand' | 'bad' | 'muted'
}

export interface D1Data {
  hospital: string
  period: string
  home: {
    deviation: { label: string; value: string; unit: string; note: string }
    kpis: { label: string; value: string; sub: string; valueTone: 'ink' | 'bad'; subTone: 'brand' | 'warn' }[]
  }
  todos: D1Todo[]
  report: {
    /** report id signed by 确认签收 (D1/signReport uses exactly this report) */
    id: string
    /** server overlay: sign = 待本院签收 · signed = 本院已签收 · none = 未向本院发布(未批准不外发) */
    status?: 'sign' | 'signed' | 'none' | 'check' | 'old'
    /** server overlay: when this institution signed (MM-DD HH:mm) */
    signedAt?: string
    meta: string
    title: string
    pages: string
    pendingLabel: string
    sections: { n: string; t: string }[]
  }
  alert: {
    /** alert row answered by 提交回执 (D1/submitReceipt alertId) — the institution's own 已发送 提醒函 */
    id?: string
    level: string
    meta: string
    title: string
    value: string
    unitNote: string
    /** 12 bar heights, % of chart height */
    bars: number[]
    attribution: string
    pendingNote: string
    doneNote: string
  }
  receiptCategories: string[]
  messages: D1Message[]
  me: { name: string; title: string; org: string; rows: { k: string; v: string }[] }
  /** server overlay: only a 定点医疗机构 identity signs / answers for its own institution */
  canSign?: boolean
  /** server overlay: the 提醒函 of `alert` was already answered by this institution */
  receiptDone?: boolean
  /** server overlay (also on B1 / B4 / cockpit for institution identities): this institution's 待签收 state */
  reportSignoff?: ReportSignoff
  /** server scope: the viewer's institution has no data of its own (payload emptied) → 暂无本院数据 */
  noOwnData?: boolean
}

/** per-institution sign-off summary served as `reportSignoff` (ReportScope on the core service) */
export interface ReportSignoff {
  pending: number
  pendingReports: { id: string; name: string }[]
  signedReports: { id: string; name: string; signedAt: string }[]
}

export const D1_SEED: D1Data = {
  hospital: '示例市第一人民医院',
  period: '2026年8月',
  home: {
    deviation: { label: '医保记账 vs DRG 支付 偏离', value: '−246.7', unit: '万', note: '−5.1% · 逆差主要来自 BR25、IU29' },
    kpis: [
      { label: 'CMI', value: '1.12', sub: '同级 P68', valueTone: 'ink', subTone: 'brand' },
      { label: '例均基金差额', value: '+723', sub: '同级 P45', valueTone: 'bad', subTone: 'warn' },
    ],
  },
  todos: [
    { id: 'sign', label: '8 月月度报告待签收', target: 'report', tone: 'warn', doneLabel: '已签收' },
    { id: 'receipt', label: 'IU29 提醒函 · 需回执', target: 'alert', tone: 'bad', doneLabel: '已回执' },
    { id: 'verify', label: 'BR25 专题核对 · 剩 2 天', target: 'B5', tone: 'violet', doneLabel: '' },
  ],
  report: {
    id: 'R-2026-08',
    meta: '月度报告 · v1 · 09-12 发布',
    title: '2026年8月 本院医保运行报告',
    pages: '18 页',
    pendingLabel: '待签收',
    sections: [
      { n: '§1 运行概况', t: '本月医保记账 4,862 万,DRG 支付 4,616 万,偏离 −5.1%,较上月收窄 0.4 个百分点。' },
      { n: '§2 病组结构', t: '逆差前三病组为 BR25、GG19、IC29,合计贡献逆差 49%。' },
      { n: '§3 同级对标', t: 'CMI 1.12 位于同级 P68;例均基金差额 +723 位于 P45。' },
      { n: '§6 下月关注', t: '重点关注 BR25、IU29 的高值耗材使用与编码。' },
    ],
  },
  alert: {
    id: 'AL-07',
    level: '高',
    meta: '提醒函 · 09-28 发出',
    title: 'IU29 例均基金差额超阈值',
    value: '+1,620',
    unitNote: '元 · 阈值 +1,500',
    bars: [11, 18, 26, 37, 42, 47, 54, 63, 68, 76, 87, 95],
    attribution: '近 3 月高值耗材使用率由 12% 升至 21%,建议核实新开展术式或编码变化。',
    pendingNote: '需在 10 个工作日内回执 · 剩 4 天',
    doneNote: '✓ 回执已提交 · 等待医保局答复',
  },
  receiptCategories: ['新开展术式', '编码调整', '患者结构变化', '其他'],
  messages: [
    { label: '8 月月度报告已发布', time: '09-12 · 待签收', doneTime: '已签收', task: 'sign', target: 'report', tone: 'brand' },
    { label: 'IU29 提醒函', time: '09-28 · 需回执', doneTime: '已回执', task: 'receipt', target: 'alert', tone: 'bad' },
    { label: '意见已答复 · 医保外费用口径', time: '09-20', doneTime: '', task: null, target: null, tone: 'muted' },
    { label: '7 月报告已更正为 v2', time: '08-26', doneTime: '', task: null, target: null, tone: 'muted' },
  ],
  me: {
    name: '李敏',
    title: '医保办主任',
    org: '示例市第一人民医院 · 本院具名',
    rows: [
      { k: '数据范围', v: '本院具名' },
      { k: '登录方式', v: '统一身份认证' },
      { k: '签收记录', v: '' },
      { k: '水印', v: '已开启' },
    ],
  },
}

/**
 * ACTIONS:
 * signReport({ reportId: string, report: string, version: string }) — 确认签收 report `reportId` for the caller's own
 *   institution (定点医疗机构 identity only; again → refused). Recorded per institution, visible in B4 and A8 签收追踪.
 * submitReceipt({ alertId: string, alert: string, category: string, text: string }) — 提交回执 for the IU29 提醒函
 *   (hospital only, own 已发送 alert; category = one of receiptCategories; text non-empty; refused otherwise);
 * requestReview({ alert: string }) — 申请复核 of the alert;
 * markRead({ message: string }) — mark a plain notice in 消息 as read.
 */
