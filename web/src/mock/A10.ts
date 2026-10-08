/** A10 意见与申诉 — seed data (承办人 view). Same shape as GET /api/v1/pages/A10. */

export type FeedbackType = '申诉' | '意见' | '纠错' | '监督建议'
export type FeedbackStatus = 'todo' | 'doing' | 'reply' | 'over' | 'done'

export interface A10Item {
  id: string
  type: FeedbackType
  org: string
  title: string
  /** location inside the report (章节 / 指标卡) */
  section: string
  /** related report */
  report: string
  /** working days left before SLA; negative = overdue */
  daysLeft: number
  status: FeedbackStatus
  /** 承办人; empty = unassigned */
  assignee: string
  text: string
  attachments: string[]
  /** SLA due date (MM-DD, 5 个工作日 from submission) — from the server */
  dueDate?: string
  /** real processing track (server): who did what, when */
  track?: A10ItemTrack
}

export interface A10ItemTrack {
  submittedAt: string
  submittedBy?: string
  assignedAt?: string
  assignedBy?: string
  repliedAt?: string
  repliedBy?: string
  reply?: string
  correction?: boolean
}

export interface A10Template {
  label: string
  /** reply draft; `{title}` is replaced with the item title */
  text: string
  /** whether choosing this template pre-ticks 触发报告更正 */
  triggersCorrection: boolean
}

export interface A10Data {
  items: A10Item[]
  /** KPIs computed by the server from the live queue (未闭环 / 已超期 are derived from items) */
  stats: { replyRate: string; avgReplyDays: string; corrections: string; slaDays?: number }
  templates: A10Template[]
  /** default assignee for one-click 分派 */
  assignTo: { name: string; team: string }
  /** fallback timestamps for the processing track (seed only; items carry their own `track` from the server) */
  track: { submittedAt: string; assignedAt: string; assignedBy: string }
}

export const A10_SEED: A10Data = {
  items: [
    { id: 'YJ-0931', type: '申诉', org: '示例市第一人民医院', title: 'BR25 特例单议病例未剔除', section: '§4 差异归因', report: 'BR25 专题核对稿', daysLeft: 2, status: 'todo', assignee: '', text: '本院 12 例经特例单议批复(批复文号见附件),不应计入偏离组均值,请复核后剔除。', attachments: ['特例单议批复.pdf', '病例清单.xlsx'] },
    { id: 'YJ-0928', type: '意见', org: '甲县人民医院', title: 'ES35 再住院率口径', section: '指标卡 14天再住院率', report: '2026年8月 DRG月度运行报告', daysLeft: -3, status: 'over', assignee: '王倩', text: '14 天再住院是否包含计划内再入院(如化疗周期)?本院数据被高估。', attachments: [] },
    { id: 'YJ-0925', type: '纠错', org: '某肛肠专科医院', title: 'GG19 病例数与本院统计不一致', section: '§2 费用结构', report: '2026年8月 DRG月度运行报告', daysLeft: -1, status: 'over', assignee: '王倩', text: '报告显示 820 例,本院 HIS 统计为 796 例,差 24 例。', attachments: ['HIS导出.xlsx'] },
    { id: 'YJ-0922', type: '意见', org: '示例市第三人民医院', title: '建议增加 CMI 同级趋势', section: '—', report: '2026年8月 DRG月度运行报告', daysLeft: 5, status: 'doing', assignee: '李华', text: '希望在月度报告中加入近 12 月 CMI 同级分位趋势。', attachments: [] },
    { id: 'YJ-0919', type: '意见', org: '乙县中医院', title: '中医优势病种口径', section: '指标卡 中医优势病种例均费用', report: '2026Q3 中医优势病种专题', daysLeft: 3, status: 'reply', assignee: '王倩', text: '中医优势病种目录是否按 2026 版?', attachments: [] },
    { id: 'YJ-0915', type: '申诉', org: '示例市中医院', title: 'IU29 编码质控扣分', section: '§3 关键行为', report: '2026年8月 DRG月度运行报告', daysLeft: 4, status: 'doing', assignee: '张悦', text: 'R-117 规则命中 9 例,本院认为主诊断与主手术匹配,附病案首页。', attachments: ['病案首页 9 份.zip'] },
    { id: 'YJ-0910', type: '意见', org: '示例市第一人民医院', title: '医保外费用口径', section: '指标卡 医保外费用占比', report: '2026年7月 DRG月度运行报告', daysLeft: 0, status: 'done', assignee: '王倩', text: '医保外费用是否包含特需服务?', attachments: [] },
  ],
  stats: { replyRate: '78', avgReplyDays: '3.2', corrections: '2' },
  templates: [
    { label: '口径解释', text: '关于“{title}”:经核实,该指标按国家医保局 2026 版口径计算,计划内再入院已剔除,详见指标卡说明。', triggersCorrection: false },
    { label: '已采纳 · 下期更正', text: '经核实,您反映的问题属实。我们将在下期报告中更正,并在报告中心发布更正说明。', triggersCorrection: true },
    { label: '已采纳 · 本期发布更正', text: '经核实,您反映的问题属实。本期报告将发布更正版 v2,原版本保留可查。', triggersCorrection: true },
    { label: '不予采纳 · 附理由', text: '经核实,该数据与结算明细一致(批次 B20260905-01),暂不予更正。差异原因:本院 HIS 统计口径包含未结算病例。', triggersCorrection: false },
  ],
  assignTo: { name: '张悦', team: '委托分析团队' },
  track: { submittedAt: '09-28 10:12', assignedAt: '09-28 14:30', assignedBy: '陈志远' },
}

/**
 * ACTIONS:
 * assignFeedback({ id: string, assignee: string, team: string }) — 分派 / 改派 an open item (never a closed one); assignee must be a
 *   known user; status todo → 'doing'; the track records who assigned it and when. SLA 5 个工作日 from submission.
 * replyFeedback({ id: string, template: string, text: string, triggerCorrection: boolean }) — send the (edited, non-empty) reply to the
 *   institution, once; status → 'done'; the reply is shown to the institution in B5 (处理进度) / C3 (我的建议). When triggerCorrection,
 *   also create a 报告更正 task in the 发布工作流 (A8).
 */
