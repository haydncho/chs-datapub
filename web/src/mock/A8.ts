/**
 * A8 发布工作流 — seed data (same JSON the backend serves at GET /api/v1/pages/A8).
 */

export type A8TaskGroup = '月告知' | '季公布' | '年通报' | '专题' | '提醒函' | '更正'

export interface A8Task {
  id: string
  group: A8TaskGroup
  name: string
  /** due text as shown, e.g. "剩 1 天" / "核对剩 2 天" / "已归档" */
  due: string
  /** current step 1–10 (10 = 归档复盘 / archived) */
  step: number
}

export interface A8Tier {
  name: string
  total: number
}

export interface A8Institution {
  name: string
  /** index into `tiers` */
  tier: number
  district: string
  batch: '第一批' | '第二批'
  /** admits DRG BR25 cases */
  br25: boolean
  /** admits DRG GG19 cases */
  gg19: boolean
}

export type A8IconKey = 'ind' | 'rpt' | 'txt' | 'qa' | 'card'

export interface A8PackageCard {
  label: string
  value: string
  unit: string
  sub: string
  /** sub-line tone */
  tone: 'muted' | 'ok'
  icon: A8IconKey
}

export type A8IndicatorStatus = 'on' | 'hold' | 'ex'

export interface A8Indicator {
  name: string
  /** 钱 · 效 · 错 */
  group: '钱' | '效' | '错'
  benchmark: string
  period: string
  status: A8IndicatorStatus
}

export interface A8PreviewRow {
  label: string
  method: string
  value: string
  tone: 'bad' | 'ok' | 'warn' | 'ink' | 'muted'
  /** not rendered for this audience (shown hatched) */
  hidden: boolean
}

export interface A8PreviewAudience {
  name: string
  identity: string
  rows: A8PreviewRow[]
}

export type A8Tab = 'pkg' | 'scope' | 'ver' | 'sig' | 'fix'

export interface A8CheckItem {
  text: string
  ok: boolean
  tab: A8Tab
}

export interface A8LogEntry {
  time: string
  who: string
  tag: string
  what: string
}

export interface A8Correction {
  oldTitle: string
  oldTag: string
  oldMeta: string
  newTitle: string
  newTag: string
  newMeta: string
  note: string
}

export interface A8Data {
  /** id of the task selected on load */
  currentTaskId: string
  tasks: A8Task[]
  steps: string[]
  /** assignee per step (index = step-1) */
  owners: string[]
  /** dwell time shown for the approval step */
  gateDwell: string
  packageVersion: string
  tiers: A8Tier[]
  districts: string[]
  institutions: A8Institution[]
  /** coverage of the previous period, for the delta */
  lastCoverage: number
  /** minimum coverage considered 范围合理 */
  minCoverage: number
  packageCards: A8PackageCard[]
  indicators: A8Indicator[]
  audiences: A8PreviewAudience[]
  previewHead: string
  /** static checklist items; the coverage item (5th) is derived in the page */
  checks: A8CheckItem[]
  phrases: string[]
  logs: A8LogEntry[]
  signPeriod: string
  signRemain: string
  /** share of covered institutions that have signed once published */
  signRate: number
  packageSize: string
  correction: A8Correction
}

const TIERS: A8Tier[] = [
  { name: '市三级', total: 6 },
  { name: '县三级', total: 4 },
  { name: '二级甲等', total: 9 },
  { name: '二级其他', total: 11 },
  { name: '一级', total: 22 },
]
const DISTRICTS = ['市区', '丙区', '甲县', '乙县']

const INSTITUTIONS: A8Institution[] = (() => {
  const out: A8Institution[] = []
  const city = ['第一人民医院', '第二人民医院', '第三人民医院', '市中医院', '市妇幼保健院', '市肿瘤医院']
  const county = ['甲县人民医院', '乙县人民医院', '甲县中医院', '乙县中医院']
  TIERS.forEach(({ total }, ti) => {
    for (let i = 0; i < total; i++) {
      const district = ti === 0 ? ['市区', '丙区'][i % 2]! : ti === 1 ? ['甲县', '乙县'][i % 2]! : DISTRICTS[(i + ti) % 4]!
      out.push({
        name: ti === 0 ? city[i]! : ti === 1 ? county[i]! : district + (ti === 4 ? '社区中心' + (i + 1) : '医院' + (i + 1)),
        tier: ti,
        district,
        batch: (i + ti) % 3 === 0 ? '第二批' : '第一批',
        br25: ti <= 2 || i % 3 === 0,
        gg19: (ti === 3 && i < 3) || (ti === 0 && i < 2) || (ti === 2 && i % 4 === 1),
      })
    }
  })
  return out
})()

export const A8_SEED: A8Data = {
  currentTaskId: 'm8',
  tasks: [
    { id: 'm8', group: '月告知', name: '2026年8月 DRG月度运行告知', due: '剩 1 天', step: 5 },
    { id: 'w8', group: '月告知', name: '2026年8月 运行预警汇总', due: '剩 4 天', step: 3 },
    { id: 'q3', group: '季公布', name: '2026年第三季度运行公布', due: '剩 18 天', step: 2 },
    { id: 'y25', group: '年通报', name: '2025年度支付方式改革通报', due: '已归档', step: 10 },
    { id: 'br25', group: '专题', name: 'BR25 脑缺血性疾患专题', due: '核对剩 2 天', step: 4 },
    { id: 'org', group: '专题', name: '2026年上半年机构体检报告', due: '剩 6 天', step: 3 },
    { id: 'gg19', group: '提醒函', name: 'GG19 次均费用预警提醒函', due: '回执剩 3 天', step: 7 },
    { id: 'c7', group: '更正', name: '2026年7月月度报告更正', due: '已归档', step: 10 },
  ],
  steps: ['计划选题', '归集校验', '分析成稿', '专家组审核', '召集人审批', '定向发布', '签收查阅', '意见申诉', '答复整改', '归档复盘'],
  owners: ['李华', '李华', '张悦', '专家组', '陈志远', '系统', '定点机构', '定点机构', '王倩', '李华'],
  gateDwell: '1 天 6 小时',
  packageVersion: '发布包 v3 · 整包审批',
  tiers: TIERS,
  districts: DISTRICTS,
  institutions: INSTITUTIONS,
  lastCoverage: 52,
  minCoverage: 40,
  packageCards: [
    { label: '指标集', value: '12', unit: '项', sub: '必选 8 · 增选 4', tone: 'muted', icon: 'ind' },
    { label: '月度运行报告', value: '18', unit: '页', sub: 'v3 · 七段齐备', tone: 'muted', icon: 'rpt' },
    { label: '解读', value: '6', unit: '段', sub: '已人工审定', tone: 'ok', icon: 'txt' },
    { label: '常见问答', value: '9', unit: '条', sub: '含 2 条典型', tone: 'muted', icon: 'qa' },
    { label: '方法卡', value: '4', unit: '张', sub: '选题 · 归因 · 标杆 · 异常', tone: 'muted', icon: 'card' },
  ],
  indicators: [
    { name: '例均基金差额', group: '钱', benchmark: '匿名分位', period: '2026-08', status: 'on' },
    { name: '次均总费用', group: '钱', benchmark: '匿名分位', period: '2026-08', status: 'on' },
    { name: 'CMI 值', group: '效', benchmark: '匿名分位', period: '2026-08', status: 'on' },
    { name: '费用消耗指数', group: '效', benchmark: '匿名分位', period: '2026-08', status: 'on' },
    { name: '时间消耗指数', group: '效', benchmark: '匿名分位', period: '2026-08', status: 'on' },
    { name: '结算清单质控率', group: '错', benchmark: '具名排行', period: '2026-08', status: 'on' },
    { name: '异地就医支出占比', group: '钱', benchmark: '匿名分位', period: '数据待到', status: 'hold' },
    { name: '单病例费用明细', group: '错', benchmark: '—', period: '—', status: 'ex' },
    { name: '参保人就医轨迹', group: '错', benchmark: '—', period: '—', status: 'ex' },
  ],
  previewHead: '示例市医保数据工作组 · 定向发布',
  audiences: [
    {
      name: '第一人民医院',
      identity: '市三级 · 本院具名',
      rows: [
        { label: '例均基金差额', method: '本院 · 同级匿名分位', value: '+486 · P62', tone: 'bad', hidden: false },
        { label: 'CMI', method: '本院 · 同级匿名分位', value: '1.12 · P68', tone: 'ink', hidden: false },
        { label: '清单质控率', method: '具名排行', value: '第 3 / 6', tone: 'ink', hidden: false },
        { label: '他院明细', method: '匿名机构不渲染', value: '—', tone: 'muted', hidden: true },
        { label: '单病例费用明细', method: '仅内部', value: '—', tone: 'muted', hidden: true },
        { label: '医保外费用占比', method: '本院', value: '6.8%', tone: 'ink', hidden: false },
      ],
    },
    {
      name: '甲县社区5',
      identity: '一级 · 本院具名',
      rows: [
        { label: '次均总费用', method: '本院 · 同级匿名分位', value: '3,120 · P41', tone: 'ink', hidden: false },
        { label: '例均基金差额', method: '本院', value: '−64', tone: 'ok', hidden: false },
        { label: '病组明细', method: '病例 < 30 并入其他', value: '6 组', tone: 'warn', hidden: false },
        { label: 'CMI 分位', method: '同级仅 3 家 · 小样本抑制', value: '—', tone: 'muted', hidden: true },
        { label: '他院明细', method: '不渲染', value: '—', tone: 'muted', hidden: true },
        { label: '单病例费用明细', method: '仅内部', value: '—', tone: 'muted', hidden: true },
      ],
    },
    {
      name: '甲县医保局',
      identity: '县区 · 本县具名',
      rows: [
        { label: '甲县人民医院', method: '本县具名', value: '+712', tone: 'bad', hidden: false },
        { label: '甲县中医院', method: '本县具名', value: '−138', tone: 'ok', hidden: false },
        { label: '其他县区', method: '汇总', value: '甲县第 2', tone: 'ink', hidden: false },
        { label: '医共体指标', method: '本县', value: '14 项', tone: 'ink', hidden: false },
        { label: '市区机构明细', method: '不渲染', value: '—', tone: 'muted', hidden: true },
        { label: '单病例费用明细', method: '仅内部', value: '—', tone: 'muted', hidden: true },
      ],
    },
  ],
  checks: [
    { text: '仅内部指标已排除 · 2 项', ok: true, tab: 'pkg' },
    { text: '小样本抑制已应用 · 县三级', ok: true, tab: 'ver' },
    { text: '专家组审核 3/3 通过', ok: true, tab: 'pkg' },
    { text: '机构核对 49/52 · 3 条异议转工单', ok: false, tab: 'sig' },
  ],
  phrases: ['数据口径已复核', '同意按期发布', '请补充县区解读', '请核实异议工单'],
  logs: [
    { time: '09-08 10:12', who: '李华', tag: '行政管理组', what: '提交发布包 v3 · 指标集 12 项 · 月度报告 18 页' },
    { time: '09-09 16:40', who: '专家组', tag: '3/3 通过', what: '附 2 条文字修改意见,已采纳' },
    { time: '09-10 09:05', who: '机构核对', tag: '49/52 确认', what: '3 家异议已转意见工单' },
    { time: '09-10 14:20', who: '系统', tag: '流转', what: '进入第 5 步:召集人审批' },
  ],
  signPeriod: '5 个工作日',
  signRemain: '剩 4 个工作日',
  signRate: 0.79,
  packageSize: 'v3 · 37 项',
  correction: {
    oldTitle: '7月月度报告 v1',
    oldTag: '已更正 · 原版保留',
    oldMeta: '08-11 发布 · 签收 52/52 · 只读可查',
    newTitle: '7月月度报告 v2',
    newTag: '现行',
    newMeta: '09-03 更正发布 · 经复核与审批',
    note: '甲县人民医院 BR25 例均基金差额由 +1,320 元更正为 +960 元;原因:补传 7 月清算数据 412 条;受影响机构 3 家已重新签收。',
  },
}

/**
 * ACTIONS:
 * approvePublish({ taskId: string, comment: string, coverage: number, institutions: string[],
 *   filters: { tiers: number[], districts: string[], batch: string, drg: string } })
 *   — 召集人批准发布包, task moves to step 6 定向发布, pushes to the covered institutions; appends 2 log entries.
 * rejectPublish({ taskId: string, toStep: number, comment: string })
 *   — 召集人驳回 (comment required), task returns to step `toStep` (1–4); appends a log entry.
 * urgeSign({ taskId: string, institution: string })
 *   — 向单家未签收机构发送催办(政务微信 + 短信).
 * urgeSignAll({ taskId: string, institutions: string[], count: number })
 *   — 一键催办 all unsigned institutions.
 * startCorrection({ taskId: string }) — 发起更正 (re-enters 专家组审核 + 召集人审批).
 * startWithdraw({ taskId: string }) — 发起撤回 (re-enters 专家组审核 + 召集人审批).
 */
