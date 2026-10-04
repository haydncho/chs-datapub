/**
 * Information architecture of the platform: a five-stage main line (归集 › 配置 ›
 * 洞察 › 发布 › 反馈) plus 全息图 / 设置 / 机构端.
 * Mirrors STG in the v3 prototypes.
 */
export type PageCode =
  | 'cockpit'
  | 'A1' | 'A3' | 'A4' | 'A5' | 'A6' | 'A7' | 'A8' | 'A9' | 'A10' | 'A11' | 'A12' | 'A13' | 'A14' | 'A15'
  | 'B1' | 'B2' | 'B3' | 'B4' | 'B5' | 'B6' | 'B7' | 'C3' | 'D1'

export interface Stage {
  id: string
  n?: string
  name: string
  pages: [PageCode, string][]
}

export const STAGES: Stage[] = [
  { id: 'cock', name: '全息图', pages: [['cockpit', '全息图']] },
  { id: 's1', n: '01', name: '归集', pages: [['A3', '数据归集中心']] },
  { id: 's2', n: '02', name: '配置', pages: [['A4', '指标配置'], ['A5', '图表与报告模板'], ['A13', '展示策略']] },
  { id: 's3', n: '03', name: '洞察', pages: [['A6', '智能推荐'], ['A7', '病种专题']] },
  { id: 's4', n: '04', name: '发布', pages: [['A8', '发布工作流'], ['A9', '流程设计器']] },
  { id: 's5', n: '05', name: '反馈', pages: [['A10', '意见与申诉'], ['A11', '预警提醒']] },
  { id: 'gov', name: '设置', pages: [['A12', '用户权限'], ['A14', '审计日志'], ['A1', '登录与身份'], ['A15', '外观配置']] },
  { id: 'aud', name: '机构端', pages: [['B1', '本院全息'], ['B2', '病组下钻'], ['B3', '对标PK'], ['B4', '报告中心'], ['B5', '意见核对'], ['B6', '政策培训'], ['B7', '区域外'], ['C3', '外部监督'], ['D1', '移动端']] },
]

/** Legacy ids used by links inside the prototypes. */
export const ALIAS: Record<string, PageCode> = { ID: 'cockpit', A2: 'cockpit' }

export const ALL_PAGES: PageCode[] = STAGES.flatMap(s => s.pages.map(p => p[0]))

export function stageOf(page: PageCode): Stage {
  return STAGES.find(s => s.pages.some(p => p[0] === page)) ?? STAGES[0]!
}

export type ZoneTone = 'blue' | 'green' | 'amber'

/** Who is looking at the page — drives header identity, zone tag and watermark. */
export interface Viewer {
  name: string
  role: string
  scope: string
  zone?: { label: string; tone: ZoneTone }
  /** organisation printed in the watermark */
  org: string
}

const ADM: Viewer = { name: '李华', role: '行政管理组', scope: '示例市全域', zone: { label: '分析监测区', tone: 'blue' }, org: '示例市医保局' }
const HOS: Viewer = { name: '李敏', role: '医保办主任', scope: '示例市第一人民医院 · 本院具名', zone: { label: '发布区', tone: 'green' }, org: '示例市第一人民医院' }
const CONV = (scope: string): Viewer => ({ name: '陈志远', role: '召集人', scope, zone: { label: '分析监测区', tone: 'blue' }, org: '示例市医保局' })

export const VIEWERS: Record<PageCode, Viewer> = {
  cockpit: CONV('全域 · 全部图层'),
  A1: { name: '访客', role: '未登录', scope: '—', zone: { label: '统一身份认证', tone: 'blue' }, org: '示例市医保局' },
  A3: { ...ADM, scope: '示例市全域 · 归集监控' },
  A4: { ...ADM, scope: '示例市全域 · 指标配置' },
  A5: ADM,
  A6: { ...ADM, scope: '示例市全域 · 选题' },
  A7: { name: '张悦', role: '委托分析团队', scope: '受控分析环境 · 仅导出审核后聚合结果', zone: { label: '分析监测区 · 受控环境', tone: 'blue' }, org: '示例市医保局' },
  A8: { name: '陈志远', role: '召集人', scope: '发布审批权限', zone: { label: '分析监测区 → 发布区', tone: 'amber' }, org: '示例市医保局' },
  A9: ADM,
  A10: { name: '王倩', role: '行政管理组 · 承办人', scope: '意见与申诉 · 全部机构', zone: { label: '分析监测区', tone: 'blue' }, org: '示例市医保局' },
  A11: { ...ADM, scope: '预警监测 · 全部机构' },
  A12: CONV('全域 · 权限管理'),
  A13: CONV('全域 · 展示策略'),
  A14: { name: '赵安', role: '安全审计员', scope: '全域 · 只读审计', zone: { label: '分析监测区', tone: 'blue' }, org: '示例市医保局' },
  A15: CONV('全域 · 外观配置'),
  B1: HOS, B2: HOS, B3: HOS, B4: HOS, B5: HOS, B6: HOS, B7: HOS,
  C3: { name: '周敏', role: '社会监督员', scope: '公开汇总层', zone: { label: '公开层', tone: 'green' }, org: '公开汇总层' },
  D1: { ...HOS, scope: '移动端 · 本院具名' },
}

export const ZONE_STYLE: Record<ZoneTone, string> = {
  blue: 'bg-brand-soft text-brand',
  green: 'bg-ok-soft text-ok-ink',
  amber: 'bg-warn-soft text-warn-ink',
}

/** Stages trimmed to the pages `allowed` (all pages when null); empty stages are dropped. */
export function visibleStages(allowed: readonly string[] | null): Stage[] {
  if (!allowed) return STAGES
  return STAGES.map(s => ({ ...s, pages: s.pages.filter(([code]) => allowed.includes(code)) })).filter(s => s.pages.length > 0)
}
