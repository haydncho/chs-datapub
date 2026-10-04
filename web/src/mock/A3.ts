/** A3 数据归集中心 — seed data (same JSON as GET /api/v1/pages/A3). */

/** Status of one monthly delivery in the 近 12 期到数 strip. */
export type A3Arrival = 'ok' | 'late' | 'missing' | 'part'
/** Current-period delivery status of a source. */
export type A3SourceStatus = 'ok' | 'late' | 'part'

export interface A3Lineage {
  indicator: string
  version: string
  batch: string
  martTable: string
  sourceTable: string
}

export interface A3Source {
  name: string
  provider: string
  /** 接入方式 */
  mode: string
  /** 频次 */
  freq: string
  /** 应到 */
  due: string
  status: A3SourceStatus
  /** 质量分 0–100 */
  score: number
  /** 近 12 期到数, oldest → current */
  history: A3Arrival[]
  /** 本期数据量, 万行 */
  rowsWan: number
  /** 依赖该数据源的指标 */
  dependents: string[]
  lineage?: A3Lineage
  /** 逾期天数 (status = late) */
  overdueDays?: number
  /** 部分到数文案 (status = part) */
  partLabel?: string
}

export interface A3OrgQc {
  org: string
  /** 合并症编码率 % */
  comorbidityRate: number
  /** 结算清单质控率 % */
  listQcRate: number
}

export interface A3Rule {
  name: string
  id: string
  hits: number
}

export interface A3Data {
  period: string
  periodShort: string
  dueDate: string
  /** pipeline / KPI figures */
  stats: {
    totalRows: string
    fieldMappings: string
    orgs: number
    doctors: string
    drgGroups: number
    rules: number
    domains: number
    indicatorTotal: number
    qualityScore: string
    qualityDelta: string
    schedule: string
  }
  qcSpec: { version: string; batch: string; comorbidityThreshold: number; listQcThreshold: number }
  sources: A3Source[]
  orgQc: A3OrgQc[]
  rules: A3Rule[]
}

export const A3_SEED: A3Data = {
  period: '2026年8月期',
  periodShort: '8 月',
  dueDate: '09-05',
  stats: {
    totalRows: '1,284 万',
    fieldMappings: '1,862',
    orgs: 52,
    doctors: '4,180',
    drgGroups: 312,
    rules: 214,
    domains: 9,
    indicatorTotal: 46,
    qualityScore: '94.6',
    qualityDelta: '+0.8',
    schedule: '每日 06:00 增量',
  },
  qcSpec: { version: 'v1.3', batch: 'B20260904-02', comorbidityThreshold: 60, listQcThreshold: 95 },
  sources: [
    {
      name: '结算明细与DRG入组', provider: '市医保经办中心', mode: '库表直连', freq: '日', due: '每日 06:00',
      status: 'ok', score: 98, history: ['ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok'], rowsWan: 412.6,
      dependents: ['次均总费用', 'CMI值', '医保外费用占比'],
      lineage: { indicator: '次均总费用', version: 'v1.4', batch: 'B20260905-01', martTable: 'dm_drg_case_fee', sourceTable: 'settle_detail_202608' },
    },
    {
      name: '月结算与清算', provider: '市医保经办中心', mode: '库表直连', freq: '月', due: '次月 3 日',
      status: 'ok', score: 97, history: ['ok', 'ok', 'ok', 'ok', 'ok', 'late', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok'], rowsWan: 38.2,
      dependents: ['例均基金差额', '费用消耗指数'],
      lineage: { indicator: '例均基金差额', version: 'v2.1', batch: 'B20260905-03', martTable: 'dm_drg_case_fee', sourceTable: 'settle_detail_202608 · drg_pay_std_2026' },
    },
    {
      name: '基金收支与预算', provider: '市医保局基金科', mode: '文件上传', freq: '月', due: '次月 5 日',
      status: 'ok', score: 95, history: ['ok', 'ok', 'ok', 'ok', 'late', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok'], rowsWan: 0.4,
      dependents: ['当期结余率', '预算执行率'],
    },
    {
      name: '电子病案', provider: '三医协同平台', mode: '接口推送', freq: '日', due: '每日 08:00',
      status: 'ok', score: 91, history: ['ok', 'ok', 'late', 'ok', 'ok', 'ok', 'ok', 'ok', 'late', 'ok', 'ok', 'ok'], rowsWan: 286.1,
      dependents: ['时间消耗指数', '14天再住院率', '术前平均住院日'],
      lineage: { indicator: '时间消耗指数', version: 'v1.0', batch: 'B20260906-01', martTable: 'dm_drg_los', sourceTable: 'emr_home_page_202608' },
    },
    {
      name: '异地就医', provider: '省医保平台回流', mode: '省平台交换', freq: '月', due: '次月 5 日',
      status: 'late', score: 93, history: ['ok', 'ok', 'late', 'ok', 'ok', 'ok', 'ok', 'late', 'ok', 'ok', 'late', 'missing'], rowsWan: 21.8, overdueDays: 29,
      dependents: ['异地就医基金支出占比', '外流病种结构', '异地就医人次'],
      lineage: { indicator: '异地就医基金支出占比', version: 'v1.0', batch: 'B20260910-01', martTable: 'dm_offsite_fund', sourceTable: 'offsite_settle_202608' },
    },
    {
      name: '双通道及外购药', provider: '定点药店 · 省平台', mode: '接口推送', freq: '月', due: '次月 5 日',
      status: 'ok', score: 88, history: ['ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'late', 'ok', 'ok', 'ok', 'ok'], rowsWan: 6.3,
      dependents: ['外购药费用占比'],
    },
    {
      name: '审核与申诉', provider: '智能审核系统', mode: '库表直连', freq: '日', due: '每日 07:00',
      status: 'ok', score: 96, history: ['ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok'], rowsWan: 92.4,
      dependents: ['结算清单质控率', '审核扣款率'],
      lineage: { indicator: '结算清单质控率', version: 'v1.3', batch: 'B20260904-02', martTable: 'dm_qc_list', sourceTable: 'audit_result_202608' },
    },
    {
      name: '卫健编办财政', provider: '卫健委 · 编办 · 财政局', mode: '文件上传', freq: '年', due: '每年 3 月',
      status: 'ok', score: 90, history: ['ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok'], rowsWan: 0.1,
      dependents: ['床位使用率', '财政补助占比'],
    },
    {
      name: '患者满意度', provider: '第三方测评机构', mode: '文件上传', freq: '季', due: '季后 15 日',
      status: 'part', score: 76, history: ['ok', 'ok', 'late', 'ok', 'ok', 'late', 'ok', 'ok', 'late', 'ok', 'ok', 'part'], rowsWan: 1.2, partLabel: '部分到数 3/5',
      dependents: ['住院患者满意度'],
    },
    {
      name: '目录与政策字典', provider: '国家医保信息平台', mode: '标准下发', freq: '按需', due: '变更即下发',
      status: 'ok', score: 100, history: ['ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'ok'], rowsWan: 0.3,
      dependents: ['分组与目录映射'],
    },
  ],
  orgQc: [
    { org: '示例市第一人民医院', comorbidityRate: 78, listQcRate: 98.1 },
    { org: '示例市第二人民医院', comorbidityRate: 74, listQcRate: 97.4 },
    { org: '示例市中医院', comorbidityRate: 69, listQcRate: 96.2 },
    { org: '甲县人民医院', comorbidityRate: 63, listQcRate: 95.4 },
    { org: '乙县中医院', comorbidityRate: 61, listQcRate: 94.6 },
    { org: '乙县人民医院', comorbidityRate: 58, listQcRate: 93.8 },
    { org: '丙区第2医院', comorbidityRate: 55, listQcRate: 92.1 },
    { org: '某肛肠专科医院', comorbidityRate: 41, listQcRate: 82.0 },
  ],
  rules: [
    { name: '主诊断与主手术不匹配', id: 'R-117', hits: 214 },
    { name: '离院方式缺失', id: 'R-032', hits: 96 },
    { name: '入院日期晚于手术日期', id: 'R-088', hits: 41 },
    { name: '费用明细与总额不一致', id: 'R-203', hits: 28 },
    { name: '重复结算', id: 'R-011', hits: 6 },
  ],
}

/**
 * ACTIONS:
 * retryPull({ source: string, attempt: number }) — 重新拉取 / 重试: re-pull a late source from the
 *   provincial exchange interface (first attempt in the demo times out with HTTP 504, the retry succeeds
 *   and the source becomes 已到数);
 * notifyContact({ source: string, channels: string[] }) — 通知对接人: notify the provider's contact
 *   (政务微信 + 短信) about a late source;
 * completeQualityCheck({ period: string }) — 完成质量校验: mark the period's quality check passed and
 *   refresh the indicator mart (only offered once all sources have arrived);
 * generateMonthlyReport({ period: string }) — 生成月度报告: create the monthly report draft and hand
 *   it to the publish workflow (A8).
 */
