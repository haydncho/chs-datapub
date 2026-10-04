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
  /** 路由路径（缺省 /<小写编号>）；D1 移动端为独立布局 /m。 */
  path?: string
}

export const pagePath = (p: PageDef) => p.path ?? `/${p.key.toLowerCase()}`

const analysis = { label: '分析监测区', tone: 'primary' as Tone }
const publishZone = { label: '发布区', tone: 'success' as Tone }

export const PAGES: PageDef[] = [
  { key: 'A2', label: '全息图', group: '全息与配置', icon: 'holo', zone: analysis, summary: '一屏掌握“钱、效、错”与公开状态,定位关键少数病组并下钻', exportable: true },
  { key: 'A4', label: '指标可视化配置', group: '全息与配置', icon: 'slider', zone: analysis, summary: '不写代码定义、配置并提交新指标上线' },
  { key: 'A6', label: '智能推荐中心', group: '全息与配置', icon: 'ai', zone: analysis, summary: '算法给出候选,人工采纳 / 修改 / 否决,推荐可追溯到方法卡' },
  { key: 'A3', label: '数据归集中心', group: '归集与分析', icon: 'data', zone: analysis, summary: '监控十类数据源到数与质量,保证指标可算、可追溯' },
  { key: 'A5', label: '图表与报告模板', group: '归集与分析', icon: 'rpt', zone: analysis, summary: '统一图表规范,用拼装器快速生成标准报告' },
  {
    key: 'A7', label: '病种专题工作台', group: '归集与分析', icon: 'topic', zone: { label: '分析监测区 · 受控分析环境', tone: 'primary' },
    summary: '七段式病种专题,所有解读须人工审定', exportable: true,
  },
  {
    key: 'A8', label: '发布工作流', group: '审批发布', icon: 'send', zone: { label: '发布工作流 · 监测区 → 发布区', tone: 'warning' },
    summary: '分析监测区数据只能经十步工作流、整包审批后进入发布区',
  },
  { key: 'A9', label: '流程设计器', group: '审批发布', icon: 'flow', summary: '可视化配置发布流程模板,模板带版本号' },
  { key: 'A10', label: '意见与申诉管理', group: '审批发布', icon: 'opinion', zone: { label: '发布区 · 意见通道', tone: 'success' }, summary: '承办人在时限内答复机构意见,核对期异议单独处理' },
  { key: 'A11', label: '预警提醒', group: '审批发布', icon: 'alert', zone: analysis, summary: '规则触发 → 提醒函 → 机构回执 → 整改跟踪' },
  { key: 'A12', label: '用户权限管理', group: '系统管理', icon: 'users', zone: { label: '系统管理', tone: 'muted' }, summary: '按组织、角色与五个控制维度管理权限' },
  { key: 'A13', label: '展示策略配置', group: '系统管理', icon: 'policy', zone: { label: '策略配置', tone: 'muted' }, summary: '“谁在看 × 数据归谁”落成可配置策略,并管控对标档位' },
  { key: 'A14', label: '审计日志', group: '系统管理', icon: 'audit', zone: { label: '审计', tone: 'muted' }, summary: '全量记录敏感操作,可按水印编号溯源', exportable: true },
  // ---- 医疗机构门户（本院具名 + 同级匿名分位；不出现他院名称）
  { key: 'B1', label: '本院全息图', group: '医疗机构门户', icon: 'holo', zone: publishZone, summary: '看清本院在同级中的位置,以及医保记账与DRG支付标准的偏离', exportable: true },
  { key: 'B2', label: '病组下钻与专题', group: '医疗机构门户', icon: 'topic', zone: publishZone, summary: '本院重点病组的费用结构、关键行为与标杆差距', exportable: true },
  { key: 'B3', label: '对标与PK', group: '医疗机构门户', icon: 'rpt', zone: publishZone, summary: '按指标对标档位呈现分位条 / 匿名编号 / 具名PK' },
  { key: 'B4', label: '报告中心', group: '医疗机构门户', icon: 'doc', zone: publishZone, summary: '接收定向发布的报告并签收,预览带实名水印' },
  { key: 'B5', label: '意见与机构核对', group: '医疗机构门户', icon: 'opinion', zone: publishZone, summary: '核对期内确认数据;提交意见必须关联具体指标或报告段落' },
  { key: 'B6', label: '政策指南与培训', group: '医疗机构门户', icon: 'book', zone: publishZone, summary: '查询分组方案、基准点数、特例单议文件,并完成培训' },
  { key: 'B7', label: '区域外数据', group: '医疗机构门户', icon: 'map', zone: publishZone, summary: '本市参保人外出就医的汇总情况' },
  { key: 'D1', label: '移动端摘要', group: '医疗机构门户', icon: 'phone', zone: { label: '发布区 · 移动端', tone: 'success' }, summary: '政务 APP 内快速查看本院摘要、预警和待签收', path: '/m' },
  // ---- 其他视图
  { key: 'C1', label: '县区医保视图', group: '县区视图', icon: 'inst', zone: publishZone, summary: '本县区机构具名数据,其他县区只看汇总与排名', exportable: true },
  { key: 'C2', label: '省级与区域外汇总', group: '汇总视图', icon: 'rpt', zone: { label: '发布区 · 汇总层', tone: 'success' }, summary: '各统筹区发布情况与监测汇总层,无机构级字段', exportable: true },
  { key: 'C3', label: '外部监督只读席位', group: '只读席位', icon: 'shield', zone: { label: '发布区 · 只读', tone: 'success' }, summary: '有效期内只读查阅发布会材料,不提供下载、打印、导出' },
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
