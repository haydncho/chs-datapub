import type { Tone } from '@/api/types'

/**
 * 页面目录（第二批）：分组侧栏与路由共用；可见范围由服务端按身份下发（/auth/me → pages），菜单按其裁剪。
 * 区标签：分析监测区（蓝）/ 发布区（绿）/ 系统管理与审计（灰）。
 */
export interface PageDef {
  key: string
  label: string
  group: string
  icon: string
  zone?: { label: string; tone: Tone }
  /** 页面顶部的一句话说明（标题行右侧弱化文字）。 */
  summary: string
  /** 可导出（导出审批）的页面。 */
  exportable?: boolean
}

const analysis = { label: '分析监测区', tone: 'primary' as Tone }

export const PAGES: PageDef[] = [
  { key: 'A3', label: '数据归集中心', group: '归集与分析', icon: 'data', zone: analysis, summary: '监控十类数据源到数与质量,保证指标可算、可追溯' },
  { key: 'A5', label: '图表与报告模板', group: '归集与分析', icon: 'rpt', zone: analysis, summary: '统一图表规范,用拼装器快速生成标准报告' },
  {
    key: 'A7', label: '病种专题工作台', group: '归集与分析', icon: 'topic', zone: { label: '分析监测区 · 受控分析环境', tone: 'primary' },
    summary: '七段式病种专题,所有解读须人工审定', exportable: true,
  },
  { key: 'A9', label: '流程设计器', group: '审批发布', icon: 'flow', summary: '可视化配置发布流程模板,模板带版本号' },
  { key: 'A10', label: '意见与申诉管理', group: '审批发布', icon: 'opinion', zone: { label: '发布区 · 意见通道', tone: 'success' }, summary: '承办人在时限内答复机构意见,核对期异议单独处理' },
  { key: 'A11', label: '预警提醒', group: '审批发布', icon: 'alert', zone: analysis, summary: '规则触发 → 提醒函 → 机构回执 → 整改跟踪' },
  { key: 'A12', label: '用户权限管理', group: '系统管理', icon: 'users', zone: { label: '系统管理', tone: 'muted' }, summary: '按组织、角色与五个控制维度管理权限' },
  { key: 'A13', label: '展示策略配置', group: '系统管理', icon: 'policy', zone: { label: '策略配置', tone: 'muted' }, summary: '“谁在看 × 数据归谁”落成可配置策略,并管控对标档位' },
  { key: 'A14', label: '审计日志', group: '系统管理', icon: 'audit', zone: { label: '审计', tone: 'muted' }, summary: '全量记录敏感操作,可按水印编号溯源', exportable: true },
]

export const pageDef = (key: string) => PAGES.find((p) => p.key === key)

export function navGroups(visible: string[]) {
  const groups: { label: string; items: PageDef[] }[] = []
  for (const p of PAGES) {
    if (!visible.includes(p.key)) continue
    let g = groups.find((x) => x.label === p.group)
    if (!g) groups.push((g = { label: p.group, items: [] }))
    g.items.push(p)
  }
  return groups
}
