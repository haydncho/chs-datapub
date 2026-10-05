import { computed, reactive } from 'vue'
import type { Viewer } from './nav'

/**
 * Login session from the unified identity service (`/api/v1/auth/*`).
 *
 * Reactive and persisted in sessionStorage (per browser tab, cleared when the tab closes).
 * Without a backend the login page builds a local demo session (`demo: true`, no token) so the route
 * guard behaves exactly as with the server.
 */

export type IdentityTone = 'brand' | 'ok'

/** 端: 医保局端(分析监测区)/ 机构端(发布区) — 与后端 user_identity.side 一致 */
export type Side = 'bureau' | 'org'

export const SIDE_NAME: Record<Side, string> = { bureau: '医保局端', org: '机构端' }

/** One identity the user holds — same card fields as `A1Identity` plus role/org. */
export interface SessionIdentity {
  id: number
  initial: string
  name: string
  desc: string
  zone: string
  tone: IdentityTone
  target: string
  who?: 'conv' | 'hosp' | 'county' | 'prov'
  role: string
  roleName: string
  dataScope: string
  orgId?: string
  orgName?: string
  scope: string
  /** 身份所属的端 */
  side: Side
}

/** Body of `GET /auth/me`; login / identity switch return the same plus `token`. */
export interface SessionInfo {
  user: { login: string; name: string }
  identity: SessionIdentity
  identities: SessionIdentity[]
  viewer: Viewer
  /** screens the current identity may open */
  pages: string[]
  readOnly: boolean
  /** ISO instant */
  expiresAt: string
}

export interface Session extends SessionInfo {
  /** bearer token; empty for a local demo session */
  token: string
  /** 演示会话:后端不可达时在前端本地建立,没有 token,pages 由 ROLE_PAGES 计算 */
  demo?: boolean
}

/* ---------- 角色 → 可访问页面(演示会话用) */

/**
 * 角色 × 页面矩阵 — 照抄后端 `cn.ybdata.core.security.AccessPolicy`(PAGES / ALL_PAGES),
 * 必须与之保持一致;修改后端矩阵时同步修改这里。
 *   convener 全部 · admin cockpit + A* · analyst A1 A6 A7 · hospital A1 cockpit B* D1
 *   county A1 cockpit A11 C3 · auditor A1 A14(只读) · observer A1 C3
 */
const ALL_PAGES = [
  'cockpit', 'A1', 'A3', 'A4', 'A5', 'A6', 'A7', 'A8', 'A9', 'A10', 'A11', 'A12', 'A13', 'A14', 'A15',
  'B1', 'B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'C3', 'D1',
]
const A_PAGES = ALL_PAGES.filter(p => p.startsWith('A'))
const B_PAGES = ALL_PAGES.filter(p => p.startsWith('B'))
const ROLE_PAGES: Record<string, readonly string[]> = {
  convener: ALL_PAGES,
  admin: ['cockpit', ...A_PAGES],
  analyst: ['A1', 'A6', 'A7'],
  hospital: ['A1', 'cockpit', ...B_PAGES, 'D1'],
  county: ['A1', 'cockpit', 'A11', 'C3'],
  auditor: ['A1', 'A14'],
  observer: ['A1', 'C3'],
}
/** 只看不改的角色(AccessPolicy.READ_ONLY) */
const READ_ONLY_ROLES = ['auditor']

/** 角色可打开的页面,按导航顺序(同 AccessPolicy.pagesFor) */
export function pagesForRole(role: string): string[] {
  const allowed = ROLE_PAGES[role] ?? []
  return ALL_PAGES.filter(p => allowed.includes(p))
}

/** 演示会话的有效期(与后端 8h 一致) */
const DEMO_TTL_MS = 8 * 3600_000

/** 由身份计算顶栏身份块(同后端 AuthService.viewer) */
function viewerOf(name: string, i: SessionIdentity): Viewer {
  return {
    name,
    role: i.roleName,
    scope: i.scope,
    zone: { label: i.zone, tone: i.tone === 'ok' ? 'green' : 'blue' },
    org: i.orgName ?? '',
  }
}

/** 建立/更新演示会话的字段(当前身份 + 该端的身份列表) */
function demoInfo(user: { login: string; name: string }, identity: SessionIdentity, held: SessionIdentity[], expiresAt: string): Session {
  return {
    token: '',
    demo: true,
    user: { login: user.login, name: user.name },
    identity,
    identities: held.filter(x => x.side === identity.side),
    viewer: viewerOf(user.name, identity),
    pages: pagesForRole(identity.role),
    readOnly: READ_ONLY_ROLES.includes(identity.role),
    expiresAt,
  }
}

/** 演示登录:按所选端过滤身份,第一个为当前身份。该端没有身份时返回 null。 */
export function buildDemoSession(user: { login: string; name: string }, held: SessionIdentity[], side: Side): Session | null {
  const mine = held.filter(x => x.side === side)
  const first = mine[0]
  if (!first) return null
  return demoInfo(user, first, mine, new Date(Date.now() + DEMO_TTL_MS).toISOString())
}

/** 演示会话内切换身份(只在所选端内,与后端 403 规则一致);成功返回新会话。 */
export function switchDemoIdentity(s: Session, identityId: number): Session | null {
  const next = s.identities.find(x => x.id === identityId)
  if (!next || next.side !== s.identity.side) return null
  return demoInfo(s.user, next, s.identities, s.expiresAt)
}

const KEY = 'yb.session'

function load(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as Session
    // sessions saved before 端(side)existed are dropped — the user logs in again
    if ((!s?.token && !s?.demo) || !s.identity?.side || !s.expiresAt || Date.parse(s.expiresAt) <= Date.now()) {
      sessionStorage.removeItem(KEY)
      return null
    }
    return s
  } catch {
    return null
  }
}

export const session = reactive<{ current: Session | null }>({ current: load() })

export function setSession(s: Session | null) {
  session.current = s
  try {
    if (s) sessionStorage.setItem(KEY, JSON.stringify(s))
    else sessionStorage.removeItem(KEY)
  } catch { /* storage unavailable — keep in memory only */ }
}

/** Replace the session's details (e.g. after `GET /auth/me`) keeping the token. */
export function updateSession(info: SessionInfo) {
  if (session.current) setSession({ ...session.current, ...info })
}

export function clearSession() {
  setSession(null)
}

/** Bearer token while the session is valid. Expired sessions are dropped on access. */
export function authToken(): string | null {
  const s = session.current
  if (!s) return null
  if (Date.parse(s.expiresAt) <= Date.now()) {
    clearSession()
    return null
  }
  return s.token || null
}

export const isLoggedIn = computed(() => session.current !== null)

/** Header identity of the logged-in user (null in demo mode / logged out). */
export const sessionViewer = computed<Viewer | null>(() => session.current?.viewer ?? null)

/** Landing page of the current identity, with the cockpit `who` when relevant. */
export function landingOf(i: Pick<SessionIdentity, 'target' | 'who'>): { code: string; query?: Record<string, string> } {
  return { code: i.target, query: i.who ? { who: i.who } : undefined }
}
