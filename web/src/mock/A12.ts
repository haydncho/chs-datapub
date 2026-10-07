/** A12 用户权限 — seed data (same shape as GET /api/v1/pages/A12). 以系统真实的用户、角色、访问矩阵为准。 */

/** 端:医保局端 / 机构端 */
export type A12Side = 'bureau' | 'org'

/** 矩阵单元:access 可访问 / readonly 只读 / none 无 */
export type A12Level = 'access' | 'readonly' | 'none'

/** on 正常 / expiring 即将停用(超过 30 天未登录) / off 已停用 / pending 新增申请待复核 */
export type A12UserStatus = 'on' | 'expiring' | 'off' | 'pending'

export interface A12Page { code: string; name: string }

/** 页面分组(矩阵的列) */
export interface A12Group { id: string; name: string; pages: A12Page[] }

export interface A12Role {
  code: string
  name: string
  /** 持有该角色的用户数 */
  users: number
  side: A12Side
  /** 数据范围(app_role.data_scope) */
  dataScope: string
  /** 可访问页面编码(= AccessPolicy.pagesFor) */
  pages: string[]
  readOnly: boolean
  canApprove: boolean
}

export interface A12Cell {
  group: string
  level: A12Level
  /** 该角色在此分组内可访问的页面 */
  pages: A12Page[]
  /** 分组内页面总数 */
  total: number
}

export interface A12MatrixRow { role: string; name: string; cells: A12Cell[] }

export interface A12Identity { roleCode: string; role: string; org: string; side: A12Side }

export interface A12User {
  login: string
  name: string
  role: string
  roleCode: string
  /** 角色补充说明,如 承办人 */
  title?: string
  org: string
  scope: string
  side: A12Side
  /** 持有身份所覆盖的端 */
  sides: A12Side[]
  identities: A12Identity[]
  /** 展示文案:今天 09:02 / 昨天 / 3 天前 / 08-26 */
  lastLogin: string
  lastLoginAt?: string
  status: A12UserStatus
  enabled: boolean
  /** pending 状态:申请人与时间 */
  requestedBy?: string
  requestedAt?: string
}

export interface A12Summary { total: number; bureau: number; org: number; expiring: number; disabled: number; pending: number }

export interface A12Data {
  /** 矩阵列名(页面分组) */
  scopes: string[]
  groups: A12Group[]
  roles: A12Role[]
  matrix: A12MatrixRow[]
  users: A12User[]
  summary: A12Summary
  /** 用户列表表头说明 */
  userSummary: string
}

const GROUP_DEFS: { id: string; name: string; pages: [string, string][] }[] = [
  { id: 'cock', name: '全景图', pages: [['cockpit', '医保数据公开全景图']] },
  { id: 's1', name: '归集', pages: [['A3', '数据归集中心']] },
  { id: 's2', name: '配置', pages: [['A4', '指标配置'], ['A5', '图表与报告模板'], ['A13', '展示策略']] },
  { id: 's3', name: '洞察', pages: [['A6', '智能推荐'], ['A7', '病种专题工作台']] },
  { id: 's4', name: '发布', pages: [['A8', '发布工作流'], ['A9', '流程设计器']] },
  { id: 's5', name: '反馈', pages: [['A10', '意见与申诉'], ['A11', '预警提醒']] },
  { id: 'gov', name: '设置', pages: [['A12', '用户权限'], ['A14', '审计日志'], ['A15', '外观配置']] },
  { id: 'org', name: '机构端功能', pages: [['B1', '本院全景'], ['B2', '病组下钻'], ['B3', '对标PK'], ['B4', '报告中心'], ['B5', '意见核对'], ['B6', '政策培训'], ['B7', '区域外患者'], ['C3', '外部监督'], ['D1', '移动端']] },
]

const GROUPS: A12Group[] = GROUP_DEFS.map(g => ({ id: g.id, name: g.name, pages: g.pages.map(([code, name]) => ({ code, name })) }))

const A_PAGES = ['A3', 'A4', 'A5', 'A6', 'A7', 'A8', 'A9', 'A10', 'A11', 'A12', 'A13', 'A14', 'A15']
const B_PAGES = ['B1', 'B2', 'B3', 'B4', 'B5', 'B6', 'B7']
const ALL = ['cockpit', 'A1', ...A_PAGES, ...B_PAGES, 'C3', 'D1']

const ROLES: A12Role[] = [
  { code: 'convener', name: '召集人', users: 1, side: 'bureau', dataScope: '全域', pages: ALL, readOnly: false, canApprove: true },
  { code: 'admin', name: '行政管理组', users: 2, side: 'bureau', dataScope: '全域', pages: ['cockpit', 'A1', ...A_PAGES], readOnly: false, canApprove: false },
  { code: 'analyst', name: '委托分析团队', users: 1, side: 'bureau', dataScope: '受控环境', pages: ['A1', 'A6', 'A7'], readOnly: false, canApprove: false },
  { code: 'hospital', name: '医保办主任', users: 1, side: 'org', dataScope: '本院具名', pages: ['cockpit', 'A1', ...B_PAGES, 'D1'], readOnly: false, canApprove: false },
  { code: 'county', name: '县区医保部门', users: 0, side: 'org', dataScope: '本县具名', pages: ['cockpit', 'A1', 'A11', 'C3'], readOnly: false, canApprove: false },
  { code: 'auditor', name: '安全审计员', users: 1, side: 'bureau', dataScope: '只读审计', pages: ['A1', 'A14'], readOnly: true, canApprove: false },
  { code: 'observer', name: '社会监督员', users: 1, side: 'org', dataScope: '公开层', pages: ['A1', 'C3'], readOnly: false, canApprove: false },
]

function matrixOf(r: A12Role): A12MatrixRow {
  return {
    role: r.code,
    name: r.name,
    cells: GROUPS.map(g => {
      const pages = g.pages.filter(p => r.pages.includes(p.code))
      const level: A12Level = pages.length === 0 ? 'none' : r.readOnly ? 'readonly' : 'access'
      return { group: g.id, level, pages, total: g.pages.length }
    }),
  }
}

const YBJ = '示例市医保局'
const H1 = '示例市第一人民医院'
const byBureau = (roleCode: string, role: string): A12Identity => ({ roleCode, role, org: YBJ, side: 'bureau' })

export const A12_SEED: A12Data = {
  scopes: GROUPS.map(g => g.name),
  groups: GROUPS,
  roles: ROLES,
  matrix: ROLES.map(matrixOf),
  users: [
    {
      login: 'chenzy', name: '陈志远', role: '召集人', roleCode: 'convener', org: YBJ, scope: '全域', side: 'bureau', sides: ['bureau', 'org'],
      identities: [byBureau('convener', '召集人'), byBureau('admin', '行政管理组'), { roleCode: 'hospital', role: '医保办主任', org: H1, side: 'org' }],
      lastLogin: '今天 09:02', status: 'on', enabled: true,
    },
    {
      login: 'lihua', name: '李华', role: '行政管理组', roleCode: 'admin', org: YBJ, scope: '全域', side: 'bureau', sides: ['bureau'],
      identities: [byBureau('admin', '行政管理组')], lastLogin: '今天 08:47', status: 'on', enabled: true,
    },
    {
      login: 'wangq', name: '王倩', role: '行政管理组', roleCode: 'admin', title: '承办人', org: YBJ, scope: '全域', side: 'bureau', sides: ['bureau'],
      identities: [byBureau('admin', '行政管理组')], lastLogin: '昨天 16:20', status: 'on', enabled: true,
    },
    {
      login: 'zhangy', name: '张悦', role: '委托分析团队', roleCode: 'analyst', org: YBJ, scope: '受控环境', side: 'bureau', sides: ['bureau'],
      identities: [byBureau('analyst', '委托分析团队')], lastLogin: '3 天前', status: 'on', enabled: true,
    },
    {
      login: 'limin', name: '李敏', role: '医保办主任', roleCode: 'hospital', org: H1, scope: '本院具名', side: 'org', sides: ['org'],
      identities: [{ roleCode: 'hospital', role: '医保办主任', org: H1, side: 'org' }], lastLogin: '今天 04:12', status: 'on', enabled: true,
    },
    {
      login: 'zhaoan', name: '赵安', role: '安全审计员', roleCode: 'auditor', org: YBJ, scope: '只读审计', side: 'bureau', sides: ['bureau'],
      identities: [byBureau('auditor', '安全审计员')], lastLogin: '2 天前', status: 'on', enabled: true,
    },
    {
      login: 'zhoumin', name: '周敏', role: '社会监督员', roleCode: 'observer', org: '公开汇总层', scope: '公开层', side: 'org', sides: ['org'],
      identities: [{ roleCode: 'observer', role: '社会监督员', org: '公开汇总层', side: 'org' }], lastLogin: '08-26', status: 'expiring', enabled: true,
    },
  ],
  summary: { total: 7, bureau: 5, org: 2, expiring: 1, disabled: 0, pending: 0 },
  userSummary: '共 7 人 · 医保局端 5 · 机构端 2 · 超过 30 天未登录 1 人即将停用',
}

/**
 * ACTIONS(仅召集人;每个操作自动进审计):
 * setUserEnabled({ login, enabled }) — 停用 / 启用账号;被停用的账号不能登录,已签发的会话立即作废。
 * requestAddUser({ name, login, role, org }) — 新增用户申请,保存为待复核(status = pending),复核通过前不建账号。
 * reviewAddUser({ login, approve }) — 由另一名召集人 / 行政管理组复核(不能复核自己提交的申请)。
 */
