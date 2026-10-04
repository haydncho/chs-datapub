/** A12 用户权限 — seed data (same shape as GET /api/v1/pages/A12). */

/** Cell value of the role × data-scope matrix. '—' = no access. */
export type A12Grant = '全' | '本院' | '本县' | '汇总' | '分位' | '聚合' | '脱敏' | '受控' | '—'

export interface A12Role {
  name: string
  /** number of users with this role */
  users: number
  /** one grant per entry of `scopes`, same order */
  grants: A12Grant[]
}

export type A12UserStatus = 'on' | 'off'

export interface A12User {
  name: string
  org: string
  role: string
  scope: string
  lastLogin: string
  /** 'off' = long-inactive, shown as 即将停用 */
  status: A12UserStatus
}

export interface A12Data {
  /** data scopes (matrix columns) */
  scopes: string[]
  roles: A12Role[]
  users: A12User[]
  /** header meta of the user list */
  userSummary: string
}

export const A12_SEED: A12Data = {
  scopes: ['全市汇总', '县区汇总', '机构具名', '机构匿名', '病组', '诊疗行为', '病例级'],
  roles: [
    { name: '召集人', users: 2, grants: ['全', '全', '全', '全', '全', '全', '脱敏'] },
    { name: '行政管理组', users: 6, grants: ['全', '全', '全', '全', '全', '全', '脱敏'] },
    { name: '委托分析团队', users: 4, grants: ['全', '全', '—', '全', '全', '全', '受控'] },
    { name: '专家组', users: 3, grants: ['全', '全', '—', '全', '全', '聚合', '—'] },
    { name: '定点医疗机构', users: 52, grants: ['汇总', '—', '本院', '分位', '本院', '本院', '—'] },
    { name: '县区医保', users: 4, grants: ['汇总', '本县', '本县', '分位', '本县', '—', '—'] },
    { name: '省级', users: 2, grants: ['汇总', '汇总', '—', '—', '—', '—', '—'] },
  ],
  users: [
    { name: '陈志远', org: '示例市医保局', role: '召集人', scope: '全域', lastLogin: '今天 09:02', status: 'on' },
    { name: '李华', org: '示例市医保局', role: '行政管理组', scope: '全域', lastLogin: '今天 08:47', status: 'on' },
    { name: '张悦', org: '受托高校团队', role: '委托分析团队', scope: '受控环境', lastLogin: '昨天', status: 'on' },
    { name: '刘教授', org: '专家组', role: '专家组', scope: '聚合数据', lastLogin: '09-30', status: 'on' },
    { name: '李敏', org: '示例市第一人民医院', role: '定点医疗机构', scope: '本院', lastLogin: '今天 09:12', status: 'on' },
    { name: '钱丽', org: '甲县医保局', role: '县区医保', scope: '甲县', lastLogin: '10-02', status: 'on' },
    { name: '孙磊', org: '乙县社区3', role: '定点医疗机构', scope: '本院', lastLogin: '07-01', status: 'off' },
  ],
  userSummary: '共 86 人 · 近 90 天未登录 4 人将自动停用',
}

/**
 * ACTIONS:
 * requestAddUser({}) — 新增用户: opens a dual-review (双人复核) request for creating a user; no user is created until the second reviewer approves.
 */
