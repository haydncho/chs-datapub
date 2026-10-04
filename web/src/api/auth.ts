import { ApiError, getJson, postJson } from './client'
import { clearSession, setSession, updateSession, type Session, type SessionInfo } from '@/app/session'

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

export async function login(method: LoginMethod, account: string, secret: string): Promise<Session> {
  const body = method === 'cert' ? { method, account, pin: secret } : { method, account, code: secret }
  const s = (await postJson<Session>('/auth/login', body)) as Session
  setSession(s)
  return s
}

/** Switch to another identity the user holds; the server revokes the old token. */
export async function selectIdentity(identity: number): Promise<Session> {
  const s = (await postJson<Session>('/auth/identity', { identity })) as Session
  setSession(s)
  return s
}

/** Refresh the session details (identities, pages, viewer). */
export async function refreshMe(): Promise<SessionInfo> {
  const me = await getJson<SessionInfo>('/auth/me')
  updateSession(me)
  return me
}

/** End the session on the server (best effort) and locally. */
export async function logout(): Promise<void> {
  try {
    await postJson('/auth/logout')
  } catch { /* already expired / offline */ }
  clearSession()
}
