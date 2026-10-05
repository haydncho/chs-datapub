/**
 * 路由守卫用到的纯函数(无副作用,便于单元验证)。
 *
 * 未登录访问 A1 以外的页面 → 跳到 `#/A1?redirect=<原目标>`;登录后回跳到该目标。
 * `redirect` 来自地址栏,不可信:只接受站内页面(`/<页面编码>` 加可选的白名单查询参数),
 * 其余一律丢弃,防止开放重定向。
 */

/** 全息图 ?who= 允许的取值 */
const WHO = ['conv', 'hosp', 'county', 'prov']

export interface Landing {
  code: string
  query?: Record<string, string>
}

/**
 * 解析并校验 redirect 参数。
 * @param raw   地址栏里的 redirect 值(如 `/A3`、`/cockpit?who=conv`)
 * @param valid 站内页面编码
 * @returns 合法的站内目标;A1 本身、未知页面、外部/协议相对/带反斜杠的地址 → null
 */
export function parseRedirect(raw: unknown, valid: readonly string[]): Landing | null {
  const v = Array.isArray(raw) ? raw[0] : raw
  if (typeof v !== 'string' || v.length > 200) return null
  const m = /^\/([A-Za-z0-9]+)(?:\?([A-Za-z0-9=&_-]*))?$/.exec(v)
  if (!m) return null
  const code = m[1]!
  if (code === 'A1' || !valid.includes(code)) return null
  const who = new URLSearchParams(m[2] ?? '').get('who')
  return who && WHO.includes(who) ? { code, query: { who } } : { code }
}

/** 把原目标编码成 redirect 参数值(不合法的目标不带回跳,返回 undefined)。 */
export function encodeRedirect(fullPath: string, valid: readonly string[]): string | undefined {
  const r = parseRedirect(fullPath, valid)
  if (!r) return undefined
  return r.query ? `/${r.code}?who=${r.query.who}` : `/${r.code}`
}

/** 登录后去哪:有合法回跳且该身份有权访问 → 回跳目标;否则身份自己的落地页。 */
export function afterLogin(redirect: unknown, allowed: readonly string[], valid: readonly string[], home: Landing): Landing {
  const r = parseRedirect(redirect, valid)
  return r && allowed.includes(r.code) ? r : home
}
