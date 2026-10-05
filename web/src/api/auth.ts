import { ApiError, getJson, postJson } from './client'
import {
  buildDemoSession, clearSession, session, setSession, switchDemoIdentity, updateSession,
  type Session, type SessionIdentity, type SessionInfo, type Side,
} from '@/app/session'
import { A1_DEMO_USERS } from '@/mock/A1'

/**
 * Unified login (server: cn.ybdata.core.auth.AuthController).
 *
 * Every function rejects with `ApiError`; `isOffline(e)` tells a missing
 * backend (demo mode — keep the prototype behaviour) from a real refusal.
 */

export type LoginMethod = 'cert' | 'sms'

export interface SmsCodeSent {
  ok: true
  /** seconds before another code may be requested */
  cooldown: number
  /** masked phone number the code went to (known accounts only) */
  phone?: string
}

/** true when no core service answered (static hosting / backend down) */
export function isOffline(e: unknown): boolean {
  return !(e instanceof ApiError) || !e.api
}

export function requestSmsCode(account: string): Promise<SmsCodeSent> {
  return postJson<SmsCodeSent>('/auth/sms-code', { account }) as Promise<SmsCodeSent>
}

/** @param side 所选端;响应里的身份列表只含该端的身份(没有则 403) */
export async function login(method: LoginMethod, account: string, secret: string, side?: Side): Promise<Session> {
  const body = method === 'cert' ? { method, account, pin: secret, side } : { method, account, code: secret, side }
  const s = (await postJson<Session>('/auth/login', body)) as Session
  setSession(s)
  return s
}

/**
 * 演示登录(后端不可达时):在前端本地建立无 token 的演示会话,字段与后端会话一致。
 * 账号取自 A1_DEMO_USERS(照抄后端种子用户),未知账号按陈志远处理;PIN / 验证码不校验(沿用原型)。
 * @throws Error 该账号在所选端下没有身份
 */
export function demoLogin(account: string, side: Side): Session {
  const key = account.trim()
  const idx = Math.max(0, A1_DEMO_USERS.findIndex(u => u.login === key || u.name === key))
  const u = A1_DEMO_USERS[idx]!
  // demo identity ids are unique per user so the cards can be told apart
  const held: SessionIdentity[] = u.identities.map((x, i) => ({ ...x, id: (idx + 1) * 10 + i }))
  const s = buildDemoSession(u, held, side)
  if (!s) throw new Error(`该账号在${side === 'org' ? '机构端' : '医保局端'}下没有可用身份,请改选另一端`)
  setSession(s)
  return s
}

/** Switch to another identity the user holds (same side only); the server revokes the old token. */
export async function selectIdentity(identity: number): Promise<Session> {
  if (session.current?.demo) {
    const s = switchDemoIdentity(session.current, identity)
    if (!s) throw new Error('该身份与当前所选端不符,请重新登录并选择该端')
    setSession(s)
    return s
  }
  const s = (await postJson<Session>('/auth/identity', { identity })) as Session
  setSession(s)
  return s
}

/** Refresh the session details (identities, pages, viewer). */
export async function refreshMe(): Promise<SessionInfo> {
  if (session.current?.demo) return session.current
  const me = await getJson<SessionInfo>('/auth/me')
  updateSession(me)
  return me
}

/** End the session on the server (best effort) and locally. */
export async function logout(): Promise<void> {
  try {
    if (!session.current?.demo) await postJson('/auth/logout')
  } catch { /* already expired / offline */ }
  clearSession()
}
