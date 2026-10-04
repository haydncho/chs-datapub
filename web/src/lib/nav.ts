import type { Tone } from '@/api/types'

/**
 * 页面目录：分组侧栏与路由共用；可见范围由服务端按身份下发（/auth/me → pages），菜单按其裁剪。
 * 页面编号（key）只作内部路由键，不在界面、提示与审计文本中出现；界面里引用页面一律用中文名（见 pageName / PageLink）。
 * 区标签只对医保局内部角色显示（见 AppLayout），机构、县区、省级、监督席位看不到。
 */
export interface PageDef {
  key: string
  label: string
  group: string
  icon: string
  zone?: Zone
  /** 页面顶部的一句话说明（标题行右侧弱化文字）。 */
  summary: string
  /** 可导出（导出审批）的页面。 */
  exportable?: boolean
  /** 路由路径（缺省 /<小写编号>）；D1 移动端为独立布局 /m。 */
  path?: string
  /** 所属工作区(见 WORKSPACES):同一工作区的页面在菜单里合并为一项,页头显示工作区页签。 */
  workspace?: string
}

/** 工作区:把同一业务任务的多个页面收成一个菜单项,页头用页签 / 步骤条在其成员间切换。 */
export interface Workspace {
  id: string
  label: string
  icon: string
  group: string
  members: string[]
  /** 成员页面按步骤编号显示(月度发布主线) */
  numbered?: boolean
}
export const WORKSPACES: Workspace[] = [
  { id: 'pub', label: '月度发布', icon: 'send', group: '核心业务', members: ['W1', 'A3', 'A5', 'A7', 'A8'], numbered: true },
  { id: 'ind', label: '指标与算法', icon: 'slider', group: '核心业务', members: ['A4', 'A6'] },
  { id: 'resp', label: '响应中心', icon: 'opinion', group: '运营与配置', members: ['A10', 'A11'] },
  { id: 'cfg', label: '策略与流程', icon: 'policy', group: '运营与配置', members: ['A13', 'A9'] },
]
export const workspaceOf = (key: string) => WORKSPACES.find((w) => w.members.includes(key))


export interface Zone {
  label: string
  tone: Tone
  /** 悬停说明。 */
  hint: string
}

export const pagePath = (p: PageDef) => p.path ?? `/${p.key.toLowerCase()}`

const analysis: Zone = { label: '分析监测区', tone: 'primary', hint: '分析监测区：医保局内部分析环境，机构不可见，数据可至病例级' }
const controlled: Zone = { label: '分析监测区 · 受控环境', tone: 'primary', hint: '受控分析环境：仅可导出经审核的聚合结果，不含病例级数据' }
const publishFlow: Zone = { label: '分析监测区 → 发布区', tone: 'warning', hint: '数据由分析监测区进入发布区的唯一通道，未批准不外发' }
const publish: Zone = { label: '发布区', tone: 'success', hint: '发布区：经审批、面向定向对象的发布内容' }

export const PAGES: PageDef[] = [
  { key: 'W0', label: '工作台', group: '总览', icon: 'grip', zone: analysis, summary: '今天要处理的事:待审批、临近超期的意见、待发出的预警,以及月度发布主线走到了哪一步' },
  { key: 'W1', label: '月度发布向导', group: '核心业务', icon: 'send', zone: publishFlow, workspace: 'pub', summary: '月度发布的完整主线:归集校验 → 分析成稿 → 专家审核 → 召集人审批 → 定向发布 → 签收 → 意见整改 → 归档' },
  { key: 'A2', label: '全息图', group: '总览', icon: 'holo', zone: analysis, summary: '一屏掌握“钱、效、错”与发布状态，定位关键少数病组并下钻', exportable: true },
  { key: 'A3', label: '数据归集中心', group: '核心业务', workspace: 'pub', icon: 'data', zone: analysis, summary: '监控十类数据源到数与质量，保证指标可算、可追溯' },
  { key: 'A4', label: '指标配置', group: '核心业务', workspace: 'ind', icon: 'slider', zone: analysis, summary: '指标可视化设计与算法模拟：不写代码定义指标、试算口径、预览呈现并提交上线' },
  { key: 'A6', label: '智能推荐中心', group: '核心业务', workspace: 'ind', icon: 'ai', zone: analysis, summary: '算法给出候选，人工采纳、修改或否决，推荐可追溯到方法卡' },
  { key: 'A7', label: '病组专题工作台', group: '核心业务', workspace: 'pub', icon: 'topic', zone: controlled, summary: '七段式病组专题，所有解读须人工审定', exportable: true },
  { key: 'A5', label: '图表与报告模板', group: '核心业务', workspace: 'pub', icon: 'rpt', zone: analysis, summary: '统一图表规范，用拼装器快速生成标准报告' },
  { key: 'A8', label: '发布工作流', group: '核心业务', workspace: 'pub', icon: 'send', zone: publishFlow, summary: '分析监测区数据只能经十步工作流、整包审批后进入发布区；另含指标上线与档位切换审批' },
  { key: 'A10', label: '意见管理', group: '运营与配置', workspace: 'resp', icon: 'opinion', zone: publish, summary: '承办人在时限内答复机构意见，核对期内的数据异议单独处理' },
  { key: 'A11', label: '预警与整改', group: '运营与配置', workspace: 'resp', icon: 'alert', zone: analysis, summary: '预警触发 → 发出提醒函 → 机构回执 → 整改跟踪' },
  { key: 'A13', label: '展示策略配置', group: '运营与配置', workspace: 'cfg', icon: 'policy', summary: '“谁在看 × 数据归谁”落成可配置策略，并管控对标档位' },
  { key: 'A9', label: '流程设计器', group: '运营与配置', workspace: 'cfg', icon: 'flow', summary: '可视化配置发布流程模板，模板带版本号' },
  { key: 'A12', label: '用户权限管理', group: '系统管理', icon: 'users', summary: '按组织、角色与五个控制维度管理权限' },
  { key: 'A14', label: '审计日志', group: '系统管理', icon: 'audit', summary: '全量记录敏感操作，可按水印编号溯源', exportable: true },
  // ---- 医疗机构门户（本院具名 + 同级匿名分位；不出现他院名称）
  { key: 'B1', label: '本院全息图', group: '医疗机构门户', icon: 'holo', summary: '看清本院在同级中的位置，以及医保记账与 DRG 支付标准的偏离', exportable: true },
  { key: 'B2', label: '病组分析', group: '医疗机构门户', icon: 'topic', summary: '本院重点病组的费用结构、关键行为与标杆差距', exportable: true },
  { key: 'B3', label: '同级对标', group: '医疗机构门户', icon: 'rpt', summary: '按指标对标档位呈现分位条、匿名编号或具名对比' },
  { key: 'B4', label: '报告中心', group: '医疗机构门户', icon: 'doc', summary: '接收定向发布的报告并签收，预览带实名水印' },
  { key: 'B5', label: '意见与核对', group: '医疗机构门户', icon: 'opinion', summary: '核对期内确认数据，提交意见，答复预警提醒函；意见须关联具体指标或报告段落' },
  { key: 'B6', label: '政策指南与培训', group: '医疗机构门户', icon: 'book', summary: '查询分组方案、基准点数、特例单议文件，并完成培训' },
  { key: 'B7', label: '区域外就医', group: '医疗机构门户', icon: 'map', summary: '本市参保人外出就医的汇总情况' },
  { key: 'D1', label: '移动端摘要（手机版）', group: '医疗机构门户', icon: 'phone', summary: '政务 APP 内快速查看本院摘要、预警和待签收', path: '/m' },
  // ---- 其他角色视图：分组名即角色，一页一组
  { key: 'C1', label: '县区医保视图', group: '县区医保', icon: 'inst', summary: '本县区机构具名数据，其他县区只看汇总与排名', exportable: true },
  { key: 'C2', label: '省级汇总', group: '省级汇总', icon: 'rpt', summary: '各统筹区发布情况与监测汇总，不含机构级字段', exportable: true },
  { key: 'E1', label: '我的导出', group: '导出', icon: 'rpt', summary: '导出申请的审批进度、有效期与带实名水印的下载；每次下载均留痕' },
  { key: 'C3', label: '外部监督席位', group: '外部监督', icon: 'shield', summary: '有效期内只读查阅发布会材料，不提供下载、打印、导出' },
]

/** 页面中文名：界面文案、提示与审计文本里引用页面时使用，不出现内部编号。 */
export const pageName = (key: string) => PAGES.find((p) => p.key === key)?.label ?? key

/** 医保局内部角色：显示区标签；外部角色（机构、县区、省级、监督席位）不显示。 */
export const INTERNAL_ROLES = ['CONVENER', 'ADMIN_GROUP', 'HANDLER', 'ANALYST', 'EXPERT', 'SECURITY_ADMIN', 'AUDITOR']

export const pageDef = (key: string) => PAGES.find((p) => p.key === key)

export interface NavEntry {
  /** data-nav / 路由目标:工作区取其第一个可见成员 */
  key: string
  label: string
  icon: string
  to: string
  /** 该菜单项覆盖的页面(用于高亮) */
  members: string[]
}

/**
 * 菜单项(按身份裁剪):同一工作区的可见成员合并为一项;只有一个可见成员时保持页面自己的名称与图标。
 * 例:召集人看到「月度发布」一项(含 归集 / 成稿 / 专题 / 审批发布 四页);意见承办人只有意见管理,显示「意见管理」。
 */
export function navGroups(visible: string[]) {
  const groups: { label: string; items: NavEntry[] }[] = []
  const push = (group: string, e: NavEntry) => {
    let g = groups.find((x) => x.label === group)
    if (!g) groups.push((g = { label: group, items: [] }))
    g.items.push(e)
  }
  const seen = new Set<string>()
  for (const p of PAGES) {
    if (!visible.includes(p.key)) continue
    const ws = p.workspace ? WORKSPACES.find((w) => w.id === p.workspace) : undefined
    if (ws) {
      if (seen.has(ws.id)) continue
      seen.add(ws.id)
      const mine = ws.members.filter((m) => visible.includes(m))
      const first = pageDef(mine[0])!
      push(ws.group, mine.length > 1 ? { key: first.key, label: ws.label, icon: ws.icon, to: pagePath(first), members: mine } : { key: first.key, label: first.label, icon: first.icon, to: pagePath(first), members: mine })
    } else {
      push(p.group, { key: p.key, label: p.label, icon: p.icon, to: pagePath(p), members: [p.key] })
    }
  }
  return groups
}
