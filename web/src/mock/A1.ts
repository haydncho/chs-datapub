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
}

export interface A1Data {
  org: { name: string; sub: string }
  slogan: [string, string]
  values: A1ValuePoint[]
  stats: A1Stat[]
  footer: string[]
  loginTabs: string[]
  /** `account`: login the detected certificate belongs to (sent with the PIN); optional — older seeds lack it */
  cert: { title: string; subject: string; account?: string }
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
  loginTabs: ['数字证书', '账号 + 短信'],
  cert: { title: '已检测到数字证书', subject: 'CN=陈志远 · 示例市医疗保障局 · 有效期至 2027-06', account: 'chenzy' },
  agreement: '登录即表示同意《医保数据定向公开管理办法》。本平台所有页面叠加个人水印,查阅与导出行为全程记录。',
  user: { name: '陈志远', authNote: '已通过证书认证 · 身份决定可见数据范围' },
  identities: [
    { initial: '市', name: '市医保局 · 召集人', desc: '全域数据 · 审批发布 · 可下钻至诊疗行为', zone: '分析监测区', tone: 'brand', target: 'cockpit', who: 'conv' },
    { initial: '管', name: '市医保局 · 行政管理组', desc: '五环节作业 · 归集、配置、发布、答复', zone: '分析监测区', tone: 'brand', target: 'A3' },
    { initial: '院', name: '定点医药机构 · 示例市第一人民医院', desc: '本院具名 · 同级匿名分位 · 签收与核对', zone: '发布区', tone: 'ok', target: 'cockpit', who: 'hosp' },
  ],
}

/**
 * ACTIONS: none via sendAction — A1 talks to the auth endpoints (src/api/auth.ts), which audit
 * every step themselves (page A1: login · loginFailed · selectIdentity · logout):
 * POST /api/v1/auth/sms-code { account } — 下发短信验证码 (60s 冷却, 429 + retryAfter when too early; demo code 123456);
 * POST /api/v1/auth/login { method: 'cert', account, pin } | { method: 'sms', account, code } — returns the session
 *   (token + user, identity, identities[] (same card fields as A1Identity + id/role/org), viewer, pages);
 * POST /api/v1/auth/identity { identity: id } — 以所选身份建立会话 (new token), then go to target (?who=);
 * POST /api/v1/auth/logout — 退出 (AppShell) / 返回登录.
 * Without a backend the page keeps the prototype behaviour (any input → 身份选择 → target page).
 */
