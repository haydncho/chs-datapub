/** A9 流程设计器 — demo data (same shape as GET /api/v1/pages/A9). */

export type A9Kind = '自动' | '人工' | '审批' | '签收' | '可选' | '并行'

export interface A9Node {
  /** 承办角色 (swimlane) */
  lane: string
  /** stage column 0…5 */
  col: number
  name: string
  kind: A9Kind
  /** 办理时限,工作日;0 = 即时 */
  days: number
}

export interface A9Flow {
  /** 月告知 / 专题 / 预警 */
  name: string
  version: number
  nodes: A9Node[]
}

export interface A9Version {
  label: string
  date: string
  note: string
  current: boolean
}

export interface A9Data {
  flows: A9Flow[]
  /** all assignable roles, in lane order */
  lanes: string[]
  /** stage column names */
  stages: string[]
  /** 法定期限 (working days) */
  legalLimit: number
  timeoutActions: string[]
  channels: string[]
  /** channels on by default for a node */
  defaultChannels: string[]
  versions: A9Version[]
}

export const A9_SEED: A9Data = {
  flows: [
    {
      name: '月告知', version: 3,
      nodes: [
        { lane: '行政管理组', col: 0, name: '生成草稿', kind: '自动', days: 1 },
        { lane: '行政管理组', col: 1, name: '数据复核', kind: '人工', days: 2 },
        { lane: '召集人', col: 2, name: '审批', kind: '审批', days: 2 },
        { lane: '定点医疗机构', col: 3, name: '签收', kind: '签收', days: 3 },
        { lane: '定点医疗机构', col: 4, name: '意见', kind: '可选', days: 10 },
        { lane: '行政管理组', col: 5, name: '答复闭环', kind: '人工', days: 5 },
      ],
    },
    {
      name: '专题', version: 3,
      nodes: [
        { lane: '委托分析团队', col: 0, name: '七段成稿', kind: '人工', days: 5 },
        { lane: '定点医疗机构', col: 1, name: '机构核对', kind: '并行', days: 3 },
        { lane: '专家组', col: 1, name: '专家审核', kind: '并行', days: 3 },
        { lane: '行政管理组', col: 2, name: '修订', kind: '人工', days: 2 },
        { lane: '召集人', col: 3, name: '审批', kind: '审批', days: 2 },
        { lane: '定点医疗机构', col: 4, name: '定向发布', kind: '签收', days: 3 },
      ],
    },
    {
      name: '预警', version: 3,
      nodes: [
        { lane: '行政管理组', col: 0, name: '预警确认', kind: '人工', days: 1 },
        { lane: '召集人', col: 1, name: '审批', kind: '审批', days: 1 },
        { lane: '定点医疗机构', col: 2, name: '回执', kind: '签收', days: 10 },
        { lane: '行政管理组', col: 3, name: '复查', kind: '自动', days: 0 },
      ],
    },
  ],
  lanes: ['行政管理组', '委托分析团队', '专家组', '召集人', '定点医疗机构'],
  stages: ['发起', '处理', '审批', '触达', '反馈', '闭环'],
  legalLimit: 15,
  timeoutActions: ['提醒承办人', '自动催办', '升级至召集人', '视为通过'],
  channels: ['站内信', '政务微信', '短信'],
  defaultChannels: ['站内信', '政务微信'],
  versions: [
    { label: 'v3 · 当前', date: '2026-07-02', note: '签收时限 5 → 3 天', current: true },
    { label: 'v2', date: '2026-03-15', note: '新增意见节点', current: false },
  ],
}

/**
 * ACTIONS:
 * publishFlowVersion({ flow: string, version: number, total: number, nodes: { name, lane, days, timeoutAction, channels: string[] }[] })
 *   — 将 flow(月告知/专题/预警)的当前编辑(承办角色、时限、超时动作、通知渠道)发布为新版本;
 *     关键路径 total 超过 legalLimit 时前端拒绝发布,不调用此动作。
 */
