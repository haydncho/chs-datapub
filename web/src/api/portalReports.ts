import { http } from './http'

/** 机构门户报告与意见（B4 报告中心 / B5 意见与机构核对 / B6 政策与培训 / D1 移动端）。只返回本院数据。 */

// ---------------------------------------------------------------- B4
export type ReportStatus = 'SIGN' | 'CHECK' | 'SIGNED' | 'OLD' | 'WITHDRAWN'
export type ReportKind = '月度报告' | '专题报告' | '体检报告'

export interface PortalReport {
  id: number
  title: string
  kind: ReportKind
  /** 发布日期 MM-DD */
  published: string
  pages: number
  status: ReportStatus
  signedAt?: string
  signedBy?: string
}

/** 预览正文块：段落或表格；diff = 末列为差额（+ 逆差红 / − 结余绿）。 */
export interface ReportBlock {
  h: string
  p?: string
  head?: string[]
  rows?: string[][]
  diff?: boolean
}

export interface PortalReportDetail extends PortalReport {
  /** 页眉：…定向发布 · 仅限本院 */
  header: string
  /** 实名水印文字：姓名 机构 水印编号 */
  watermark: string
  wmNo: string
  body: ReportBlock[]
}

// ---------------------------------------------------------------- B5
export type CheckState = 'OPEN' | 'OK' | 'OBJECT' | 'AUTO'

export interface CheckItem {
  idx: number
  label: string
  value: string
  decision?: 'OK' | 'OBJECT'
  state: CheckState
  decidedAt?: string
  ticketNo?: string
}

export interface CheckRound {
  id: number
  title: string
  reportId: number
  /** ISO 8601（含 +08:00） */
  deadline: string
  deadlineLabel: string
  /** 服务端当前时间（epoch ms），用于校正本机时钟 */
  serverNow: number
  closed: boolean
  items: CheckItem[]
}

export interface OpinionRef {
  id: number
  label: string
  reportId?: number
}

export interface MyOpinion {
  no: string
  ref: string
  category: string
  content: string
  status: 'WAIT' | 'DOING' | 'DONE'
  reply?: string
  repliedAt?: string
  rating?: number
  createdAt: string
  dueLabel?: string
}

export interface OpinionSubmit {
  refId: number | null
  category: string
  text: string
  checkItem?: number | null
}

export interface OpinionSubmitted {
  no: string
  category: string
  owner: string
  dueDate: string
  message: string
}

// ---------------------------------------------------------------- B6
export interface PolicyDoc {
  id: number
  category: string
  title: string
  docNo: string
  issuedOn: string
  format: string
}

export interface Course {
  id: number
  title: string
  minutes: number
  pct: number
}

export interface QuizGraded {
  picked: number
  correct: boolean
  correctIndex: number
  result: string
  explain: string
}

/** 题面不含正确答案；作答后才有 answered。 */
export interface Quiz {
  id: number
  courseTitle: string
  question: string
  options: string[]
  answered?: QuizGraded
}

// ---------------------------------------------------------------- D1
export interface MobileKpi {
  label: string
  value: string
  sub?: string
  tone?: 'danger' | 'success'
}

export interface MobileSummary {
  org: string
  period?: string
  pending?: { id: number; title: string }
  lastSigned?: { id: number; title: string; signedAt?: string }
  alerts: { id: number; rule: string; summary: string }[]
  kpis?: MobileKpi[]
  groups?: { code: string; name: string; diff: number }[]
  check?: { title: string; deadlineLabel: string; closed: boolean; openItems: number }
}

export interface MobileAlert {
  id: number
  rule: string
  title: string
  period: string
  value: string
  percentile?: number
  line?: number
  narrative?: string
  letterNo: string
  status: string
  group: string
}

/** 医保局发来的预警提醒函;已发函待回执的可提交回执(原因分析与整改措施)。 */
export interface AlertLetter {
  id: number
  letterNo: string
  rule: string
  group: string
  period: string
  value: string
  status: 'SENT' | 'RCPT' | 'FIX' | 'CLOSED'
  statusLabel: string
  receipt?: string
  canReceipt: boolean
}

export const portalReportsApi = {
  alerts: () => http.get<AlertLetter[]>('/portal/alerts'),
  alertReceipt: (id: number, text: string) => http.post<AlertLetter>(`/portal/alerts/${id}/receipt`, { text }),
  reports: (kind?: string) => http.get<PortalReport[]>('/portal/reports', { kind }),
  report: (id: number) => http.get<PortalReportDetail>(`/portal/reports/${id}`),
  sign: (id: number) => http.post<PortalReport>(`/portal/reports/${id}/sign`),

  check: () => http.get<{ round: CheckRound | null }>('/portal/check'),
  decide: (idx: number, decision: 'OK' | 'OBJECT') => http.put<CheckRound>(`/portal/check/items/${idx}`, { decision }),
  opinions: () => http.get<{ refs: OpinionRef[]; categories: string[]; rows: MyOpinion[] }>('/portal/opinions'),
  submitOpinion: (body: OpinionSubmit) => http.post<OpinionSubmitted>('/portal/opinions', body),
  rate: (no: string, rating: number) => http.put<MyOpinion>(`/portal/opinions/${encodeURIComponent(no)}/rating`, { rating }),

  docs: (category?: string, q?: string) => http.get<{ categories: string[]; rows: PolicyDoc[] }>('/portal/policy/docs', { category, q }),
  viewDoc: (id: number) => http.post<{ message: string }>(`/portal/policy/docs/${id}/view`),
  courses: () => http.get<Course[]>('/portal/policy/courses'),
  quiz: () => http.get<{ quiz: Quiz | null }>('/portal/policy/quiz'),
  answer: (id: number, picked: number) => http.post<QuizGraded>(`/portal/policy/quiz/${id}/answer`, { picked }),

  mobileSummary: () => http.get<MobileSummary>('/portal/mobile/summary'),
  mobileAlert: (id: number) => http.get<MobileAlert>(`/portal/mobile/alerts/${id}`),
}
