import { computed, reactive } from 'vue'
import type { Viewer } from './nav'

/**
 * Login session from the unified identity service (`/api/v1/auth/*`).
 *
 * Reactive and persisted in sessionStorage (per browser tab, cleared when the tab closes).
 * Without a backend there is never a session and every screen keeps its demo identity.
 */

export type IdentityTone = 'brand' | 'ok'

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
  token: string
}

const KEY = 'yb.session'

function load(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as Session
    if (!s?.token || !s.expiresAt || Date.parse(s.expiresAt) <= Date.now()) {
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
  return s.token
}

export const isLoggedIn = computed(() => session.current !== null)

/** Header identity of the logged-in user (null in demo mode / logged out). */
export const sessionViewer = computed<Viewer | null>(() => session.current?.viewer ?? null)

/** Landing page of the current identity, with the cockpit `who` when relevant. */
export function landingOf(i: Pick<SessionIdentity, 'target' | 'who'>): { code: string; query?: Record<string, string> } {
  return { code: i.target, query: i.who ? { who: i.who } : undefined }
}
