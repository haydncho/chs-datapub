/** A1 登录与身份 — demo data (same shape as GET /api/v1/pages/A1). */

export interface A1ValuePoint {
  /** inline SVG path (24×24 viewBox) */
  icon: string
  title: string
  desc: string
}

export interface A1Stat {
  value: string
  unit: string
  label: string
}

export type A1Tone = 'brand' | 'ok'

/** 端: 医保局端(分析监测区) / 机构端(发布区) */
export type A1Side = 'bureau' | 'org'

/** 登录页第一步「选择端」的卡片 */
export interface A1SideCard {
  id: A1Side
  title: string
  /** 工作区标签 */
  zone: string
  desc: string
  /** 该端下的身份 */
  roles: string[]
  /** 卡片底部的进入提示 */
  enter: string
}

export interface A1Identity {
  /** single-character avatar */
  initial: string
  name: string
  desc: string
  zone: string
  tone: A1Tone
  /** landing page code after 进入平台 */
  target: string
  /** cockpit identity (?who=) when target is the cockpit */
  who?: 'conv' | 'hosp' | 'county' | 'prov'
  /** 身份所属的端 */
  side: A1Side
}

export interface A1Data {
  org: { name: string; sub: string }
  slogan: [string, string]
  values: A1ValuePoint[]
  stats: A1Stat[]
  footer: string[]
  /** 第一步: 选择端 */
  sides: A1SideCard[]
  loginTabs: string[]
  /** detected certificate (display only; the account is read locally — see pages/A1/cert.ts — never served) */
  cert: { title: string; subject: string }
  agreement: string
  user: { name: string; authNote: string }
  identities: A1Identity[]
}

export const A1_SEED: A1Data = {
  org: { name: '医保数据公开 · 定向发布平台', sub: '示例市医疗保障局 · 医保数据工作组' },
  slogan: ['让每一家定点机构,', '看清自己的医保运行。'],
  values: [
    { icon: 'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3z', title: '分级可见', desc: '按身份裁剪数据范围,他院一律匿名' },
    { icon: 'M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z', title: '定向发布', desc: '月告知、专题、提醒函精准送达到机构' },
    { icon: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2', title: '全程留痕', desc: '查阅、导出、审批均可追溯至个人' },
  ],
  stats: [
    { value: '52', unit: '家', label: '定点医药机构' },
    { value: '46', unit: '期', label: '已定向发布' },
    { value: '96', unit: '%', label: '机构签收率' },
  ],
  footer: ['医保专网 · 政务云', '等保三级', '全程留痕审计'],
  sides: [
    {
      id: 'bureau',
      title: '医保局端',
      zone: '分析监测区',
      desc: '数据归集、指标配置、智能分析与发布审批,面向医保局内部人员。',
      roles: ['召集人', '行政管理组', '委托分析团队', '安全审计员'],
      enter: '进入分析监测区',
    },
    {
      id: 'org',
      title: '机构端',
      zone: '发布区',
      desc: '查看本机构的定向发布内容,完成报告签收、意见核对与监督反馈。',
      roles: ['定点医疗机构医保办', '县区医保', '外部监督'],
      enter: '进入发布区',
    },
  ],
  loginTabs: ['数字证书', '账号 + 短信'],
  cert: { title: '已检测到数字证书', subject: 'CN=陈志远 · 示例市医疗保障局 · 有效期至 2027-06' },
  agreement: '登录即表示同意《医保数据定向公开管理办法》。本平台所有页面叠加个人水印,查阅与导出行为全程记录。',
  user: { name: '陈志远', authNote: '已通过证书认证 · 身份决定可见数据范围' },
  identities: [
    { initial: '市', name: '市医保局 · 召集人', desc: '全域数据 · 审批发布 · 可下钻至诊疗行为', zone: '分析监测区', tone: 'brand', target: 'cockpit', who: 'conv', side: 'bureau' },
    { initial: '管', name: '市医保局 · 行政管理组', desc: '五环节作业 · 归集、配置、发布、答复', zone: '分析监测区', tone: 'brand', target: 'A3', side: 'bureau' },
    { initial: '院', name: '定点医药机构 · 示例市第一人民医院', desc: '本院具名 · 同级匿名分位 · 签收与核对', zone: '发布区', tone: 'ok', target: 'cockpit', who: 'hosp', side: 'org' },
  ],
}

/**
 * 演示模式(无后端)的账号表 — 照抄后端迁移 V4__auth_identity.sql(用户与身份)+ V7__identity_side.sql(side)。
 * 不是页面数据(不导出为种子,名字不以 _SEED 结尾);身份列表为该用户持有的全部身份,
 * 登录时按所选端过滤。演示 PIN / 验证码不校验(沿用原型:任意输入即可)。
 */
export interface A1DemoIdentity extends A1Identity {
  role: string
  roleName: string
  dataScope: string
  orgId: string
  orgName: string
  scope: string
}

export interface A1DemoUser {
  login: string
  name: string
  identities: A1DemoIdentity[]
}

const BUREAU_ORG = { orgId: 'YBJ', orgName: '示例市医疗保障局' }
const H001 = { orgId: 'H001', orgName: '示例市第一人民医院' }

const I_CONV: A1DemoIdentity = { initial: '市', name: '市医保局 · 召集人', desc: '全域数据 · 审批发布 · 可下钻至诊疗行为', zone: '分析监测区', tone: 'brand', target: 'cockpit', who: 'conv', side: 'bureau', role: 'convener', roleName: '召集人', dataScope: '全域', ...BUREAU_ORG, scope: '示例市全域' }
const I_ADMIN: A1DemoIdentity = { initial: '管', name: '市医保局 · 行政管理组', desc: '五环节作业 · 归集、配置、发布、答复', zone: '分析监测区', tone: 'brand', target: 'A3', side: 'bureau', role: 'admin', roleName: '行政管理组', dataScope: '全域', ...BUREAU_ORG, scope: '示例市全域' }
const I_HOSP: A1DemoIdentity = { initial: '院', name: '定点医药机构 · 示例市第一人民医院', desc: '本院具名 · 同级匿名分位 · 签收与核对', zone: '发布区', tone: 'ok', target: 'cockpit', who: 'hosp', side: 'org', role: 'hospital', roleName: '医保办主任', dataScope: '本院具名', ...H001, scope: '示例市第一人民医院 · 本院具名' }

export const A1_DEMO_USERS: A1DemoUser[] = [
  { login: 'chenzy', name: '陈志远', identities: [I_CONV, I_ADMIN, I_HOSP] },
  { login: 'lihua', name: '李华', identities: [I_ADMIN] },
  { login: 'wangq', name: '王倩', identities: [{ ...I_ADMIN, desc: '意见与申诉承办 · 全部机构', target: 'A10', scope: '意见与申诉 · 全部机构' }] },
  { login: 'zhangy', name: '张悦', identities: [{ initial: '析', name: '市医保局 · 委托分析团队', desc: '受控分析环境 · 仅导出审核后聚合结果', zone: '分析监测区 · 受控环境', tone: 'brand', target: 'A7', side: 'bureau', role: 'analyst', roleName: '委托分析团队', dataScope: '受控环境', ...BUREAU_ORG, scope: '受控分析环境' }] },
  { login: 'limin', name: '李敏', identities: [{ ...I_HOSP, target: 'B1', who: undefined }] },
  { login: 'zhaoan', name: '赵安', identities: [{ initial: '审', name: '市医保局 · 安全审计员', desc: '全域只读审计 · 不可修改业务数据', zone: '分析监测区', tone: 'brand', target: 'A14', side: 'bureau', role: 'auditor', roleName: '安全审计员', dataScope: '只读审计', ...BUREAU_ORG, scope: '全域 · 只读审计' }] },
  { login: 'zhoumin', name: '周敏', identities: [{ initial: '监', name: '社会监督员 · 公开汇总层', desc: '仅公开层汇总数据 · 可提交监督建议', zone: '公开层', tone: 'ok', target: 'C3', side: 'org', role: 'observer', roleName: '社会监督员', dataScope: '公开层', orgId: 'PUB', orgName: '公开汇总层', scope: '公开汇总层' }] },
]

/**
 * ACTIONS: none via sendAction — A1 talks to the auth endpoints (src/api/auth.ts), which audit
 * every step themselves (page A1: login · loginFailed · selectIdentity · logout):
 * POST /api/v1/auth/sms-code { account } — 下发短信验证码 (60s 冷却, 429 + retryAfter when too early; demo code 123456);
 * POST /api/v1/auth/login { method: 'cert', account, pin } | { method: 'sms', account, code } — returns the session
 *   (token + user, identity, identities[] (same card fields as A1Identity + id/role/org), viewer, pages);
 * POST /api/v1/auth/identity { identity: id } — 以所选身份建立会话 (new token), then go to target (?who=);
 * POST /api/v1/auth/logout — 退出 (AppShell) / 返回登录.
 * 登录请求可带 side ('bureau' | 'org'):身份列表只含所选端的身份(无则 403);/auth/identity 不能跨端切换(403)。
 * Without a backend the page builds a local demo session from A1_DEMO_USERS (any PIN/code → 身份选择 → target page).
 */
