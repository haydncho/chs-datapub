/**
 * Information architecture of the platform: 端(医保局端 / 机构端) › 一级菜单(分组) › 二级菜单(页面)。
 * 医保局端主线为 归集 › 配置 › 洞察 › 发布 › 反馈,另有 全息图 / 设置;机构端沿用同样的分类名。
 * Mirrors STG in the v3 prototypes.
 */
export type PageCode =
  | 'cockpit'
  | 'A1' | 'A3' | 'A4' | 'A5' | 'A6' | 'A7' | 'A8' | 'A9' | 'A10' | 'A11' | 'A12' | 'A13' | 'A14' | 'A15'
  | 'B1' | 'B2' | 'B3' | 'B4' | 'B5' | 'B6' | 'B7' | 'C3' | 'D1'

export type Side = 'bureau' | 'org'

export const SIDE_NAME: Record<Side, string> = { bureau: '医保局端', org: '机构端' }
/** 端徽标使用的图标名(见 lib/icons.ts) */
export const SIDE_ICON: Record<Side, string> = { bureau: 'landmark', org: 'hospital' }

export interface NavItem { code: PageCode; name: string; icon: string; query?: Record<string, string> }
export interface NavGroup { id: string; n?: string; name: string; icon: string; items: NavItem[] }

/** 医保局端菜单树(超集):全息图 › 01–05 主线 › 设置。登录页 A1 不在菜单中。 */
const BUREAU_NAV: NavGroup[] = [
  { id: 'cock', name: '全息图', icon: 'layout-dashboard', items: [{ code: 'cockpit', name: '全息图', icon: 'layout-dashboard', query: { who: 'conv' } }] },
  { id: 's1', n: '01', name: '归集', icon: 'database', items: [{ code: 'A3', name: '数据归集中心', icon: 'database' }] },
  {
    id: 's2', n: '02', name: '配置', icon: 'sliders-horizontal',
    items: [
      { code: 'A4', name: '指标配置', icon: 'sliders-horizontal' },
      { code: 'A5', name: '图表与报告模板', icon: 'layout-template' },
      { code: 'A13', name: '展示策略', icon: 'eye' },
    ],
  },
  {
    id: 's3', n: '03', name: '洞察', icon: 'lightbulb',
    items: [
      { code: 'A6', name: '智能推荐', icon: 'sparkles' },
      { code: 'A7', name: '病种专题', icon: 'stethoscope' },
    ],
  },
  {
    id: 's4', n: '04', name: '发布', icon: 'send',
    items: [
      { code: 'A8', name: '发布工作流', icon: 'rocket' },
      { code: 'A9', name: '流程设计器', icon: 'workflow' },
    ],
  },
  {
    id: 's5', n: '05', name: '反馈', icon: 'message-square',
    items: [
      { code: 'A10', name: '意见与申诉', icon: 'message-square-warning' },
      { code: 'A11', name: '预警提醒', icon: 'bell-ring' },
    ],
  },
  {
    id: 'gov', name: '设置', icon: 'settings',
    items: [
      { code: 'A12', name: '用户权限', icon: 'users' },
      { code: 'A14', name: '审计日志', icon: 'scroll-text' },
      { code: 'A15', name: '外观配置', icon: 'palette' },
    ],
  },
]

/** 机构端菜单树(超集):沿用医保局端的分类名,不加序号。 */
const ORG_NAV: NavGroup[] = [
  {
    id: 'cock', name: '全息图', icon: 'layout-dashboard',
    items: [
      { code: 'cockpit', name: '机构全息图', icon: 'layout-dashboard', query: { who: 'hosp' } },
      { code: 'B1', name: '本院全息', icon: 'hospital' },
    ],
  },
  {
    id: 's3', name: '洞察', icon: 'lightbulb',
    items: [
      { code: 'B2', name: '病组下钻', icon: 'layers' },
      { code: 'B3', name: '对标PK', icon: 'swords' },
      { code: 'B7', name: '区域外', icon: 'map-pinned' },
      { code: 'C3', name: '外部监督', icon: 'shield-check' },
    ],
  },
  { id: 's4', name: '发布', icon: 'send', items: [{ code: 'B4', name: '报告中心', icon: 'file-chart-column' }] },
  {
    id: 's5', name: '反馈', icon: 'message-square',
    items: [
      { code: 'B5', name: '意见核对', icon: 'clipboard-check' },
      { code: 'A11', name: '预警提醒', icon: 'bell-ring' },
      { code: 'D1', name: '移动端', icon: 'smartphone' },
    ],
  },
  { id: 'gov', name: '设置', icon: 'settings', items: [{ code: 'B6', name: '政策培训', icon: 'graduation-cap' }] },
]

const TREES: Record<Side, NavGroup[]> = { bureau: BUREAU_NAV, org: ORG_NAV }

/** 按端返回菜单树,只保留 `allowed` 允许访问的页面(null = 全部);空分组去掉。 */
export function navFor(side: Side, allowed: readonly string[] | null): NavGroup[] {
  const tree = TREES[side] ?? BUREAU_NAV
  if (!allowed) return tree
  return tree
    .map(g => ({ ...g, items: g.items.filter(i => allowed.includes(i.code)) }))
    .filter(g => g.items.length > 0)
}

/** 菜单项是否对应当前路由(全息图再按 query.who 区分两端)。 */
export function isActiveItem(item: NavItem, code: string, query?: Record<string, unknown>): boolean {
  if (item.code !== code) return false
  if (code === 'cockpit' && item.query?.who) {
    const w = query?.who
    // 机构端的 county / prov 视角也归入机构全息图
    const mine = item.query.who === 'conv' ? (w === undefined || w === 'conv') : (w !== undefined && w !== 'conv')
    return mine
  }
  return true
}

/** 定位页面所在的端 / 分组 / 菜单项;preferSide 优先(同一页面可同时出现在两端,如 A11)。 */
export function locate(code: string, query?: Record<string, unknown>, preferSide: Side = 'bureau'):
  { side: Side; group: NavGroup; item: NavItem } | null {
  const sides: Side[] = preferSide === 'bureau' ? ['bureau', 'org'] : ['org', 'bureau']
  for (const side of sides) {
    for (const group of TREES[side]) {
      const item = group.items.find(i => isActiveItem(i, code, query))
      if (item) return { side, group, item }
    }
  }
  return null
}

export function breadcrumbOf(side: Side, code: string, query?: Record<string, unknown>): { side: string; group: string; item: string } {
  const hit = locate(code, query, side)
  const sideName = SIDE_NAME[side] ?? SIDE_NAME.bureau
  if (!hit || hit.side !== side) {
    // 该端菜单中没有此页(如登录页):只显示端
    const t = PAGES_TITLE[code]
    return { side: sideName, group: hit?.group.name ?? '', item: hit?.item.name ?? t ?? '' }
  }
  return { side: sideName, group: hit.group.name, item: hit.item.name }
}

const PAGES_TITLE: Record<string, string> = { A1: '登录与身份' }

/* ---------- 兼容旧导出(旧版"五环节"结构,基于新菜单树生成) ---------- */
export interface Stage {
  id: string
  n?: string
  name: string
  pages: [PageCode, string][]
}

const toStage = (g: NavGroup): Stage => ({ id: g.id, n: g.n, name: g.name, pages: g.items.map(i => [i.code, i.name]) })

export const STAGES: Stage[] = [
  ...BUREAU_NAV.map(toStage),
  { id: 'aud', name: '机构端', pages: ORG_NAV.flatMap(g => g.items).filter(i => i.code !== 'cockpit' && i.code !== 'A11').map(i => [i.code, i.name] as [PageCode, string]) },
]

/** Legacy ids used by links inside the prototypes. */
export const ALIAS: Record<string, PageCode> = { ID: 'cockpit', A2: 'cockpit' }

export const ALL_PAGES: PageCode[] = [...new Set<PageCode>([...BUREAU_NAV, ...ORG_NAV].flatMap(g => g.items.map(i => i.code)).concat('A1'))]

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
