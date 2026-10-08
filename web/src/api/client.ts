import { ref, type Ref } from 'vue'
import { authToken, clearSession, landingOf, session } from '@/app/session'

/**
 * Thin API client.
 *
 * Every page renders immediately from its local seed (`src/mock/<page>.ts`,
 * the same demo data the backend seeds into PostgreSQL) and then swaps in the
 * server copy from `GET /api/v1/pages/{code}` when the backend is reachable.
 * Actions go to `POST /api/v1/actions/{code}/{action}`: `runAction` waits for
 * the server's answer (use it whenever the UI must only change on success);
 * `sendAction` is fire-and-forget but toasts the server's refusal, so a failed
 * call never passes silently.
 *
 * When logged in (A1), every call carries `Authorization: Bearer <token>`.
 * A 401 from the backend ends the session, says why and routes to
 * `#/A1?redirect=<current page>`; a 403 on a page payload returns to the
 * identity's own landing page.
 */
const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api/v1'

/** Error with the HTTP status; `api` is true when the core service itself answered (JSON body). */
export class ApiError extends Error {
  readonly status: number
  readonly api: boolean
  readonly retryAfter?: number
  constructor(status: number, message: string, api: boolean, retryAfter?: number) {
    super(message)
    this.status = status
    this.api = api
    this.retryAfter = retryAfter
  }
}

/** endpoints that must not bounce to the login screen on 401 (they *are* the login) */
const NO_REDIRECT = ['/auth/login', '/auth/sms-code', '/auth/logout']

/** headers for raw `fetch` calls (downloads) — carries the login token when present */
export function authHeaders(extra: Record<string, string> = {}): Record<string, string> {
  const token = authToken()
  return token ? { ...extra, Authorization: `Bearer ${token}` } : { ...extra }
}

async function request<T>(method: 'GET' | 'POST', path: string, body?: unknown): Promise<T | null> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = authToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    const isJson = (res.headers.get('Content-Type') ?? '').includes('json')
    let msg = `${res.status} ${res.statusText}`
    let retryAfter: number | undefined
    if (isJson) {
      try {
        const j = (await res.json()) as { error?: string; retryAfter?: number }
        if (j.error) msg = j.error
        retryAfter = j.retryAfter
      } catch { /* empty body */ }
    }
    if (res.status === 401 && isJson && !NO_REDIRECT.includes(path)) onUnauthorized(msg)
    throw new ApiError(res.status, msg, isJson, retryAfter)
  }
  const text = await res.text()
  return text ? (JSON.parse(text) as T) : null
}

/** several calls of one page can fail together: one notice is enough */
let unauthorizedNoticeAt = 0

function onUnauthorized(message: string) {
  // no session to end: nobody was logged in (the route guard already keeps such visitors on A1)
  if (session.current === null) return
  clearSession()
  void Promise.all([import('@/app/router'), import('@/app/guard'), import('@/app/shell')]).then(
    ([{ router }, { encodeRedirect }, { say }]) => {
      const cur = router.currentRoute.value
      if (cur.meta.code === 'A1') return
      if (Date.now() - unauthorizedNoticeAt > 3000) {
        unauthorizedNoticeAt = Date.now()
        say(`登录已失效:${message || '会话已过期或被撤销'}`)
      }
      const codes = router.getRoutes().map(r => r.name).filter((n): n is string => typeof n === 'string')
      const redirect = encodeRedirect(cur.fullPath, codes)
      void router.push({ path: '/A1', query: redirect ? { redirect } : {} })
    },
  )
}

function onForbidden(code: string, message: string) {
  const s = session.current
  if (!s) return
  void Promise.all([import('@/app/router'), import('@/app/shell')]).then(([{ router, goPage }, { say }]) => {
    if (router.currentRoute.value.meta.code !== code) return
    say(message || '当前身份无权访问该页面')
    const home = landingOf(s.identity)
    if (home.code !== code) goPage(home.code, home.query)
  })
}

export async function getJson<T>(path: string): Promise<T> {
  return (await request<T>('GET', path)) as T
}

export async function postJson<T = unknown>(path: string, body?: unknown): Promise<T | null> {
  return request<T>('POST', path, body)
}

/**
 * Page dataset: starts as `seed`, replaced by the backend payload if the
 * server answers with the same shape.
 */
export function usePageData<T extends object>(code: string, seed: T): Ref<T> {
  const data = ref(seed) as Ref<T>
  getJson<T>(`/pages/${encodeURIComponent(code)}`)
    .then(remote => {
      if (remote && typeof remote === 'object') data.value = { ...seed, ...remote }
    })
    .catch((e: unknown) => {
      if (e instanceof ApiError && e.status === 403 && e.api) onForbidden(code, e.message)
      /* otherwise: backend not running — keep seed */
    })
  return data
}

/** Result of an action call: ok with the server's data, or the server's refusal (status + message). */
export type ActionResult<T = unknown> = { ok: true; data: T | null } | { ok: false; status: number; error: string }

/** Run a user action and wait for the server's answer — use when the UI must only change after the server accepted it. */
export async function runAction<T = unknown>(code: string, action: string, payload?: unknown): Promise<ActionResult<T>> {
  try {
    return { ok: true, data: await postJson<T>(`/actions/${encodeURIComponent(code)}/${encodeURIComponent(action)}`, payload ?? {}) }
  } catch (e) {
    return e instanceof ApiError ? { ok: false, status: e.status, error: e.message } : { ok: false, status: 0, error: '网络异常,操作未保存' }
  }
}

/** Record a user action on the server (audit trail + state). Never throws. */
export function sendAction(code: string, action: string, payload?: unknown): void {
  postJson(`/actions/${encodeURIComponent(code)}/${encodeURIComponent(action)}`, payload ?? {}).catch((e: unknown) => {
    // a refusal (4xx/5xx from the core service) is shown as a toast; 401 is handled by the session
    // redirect, and an unreachable backend stays silent in the offline demo (no token)
    let message: string | null = null
    if (e instanceof ApiError && e.api) message = e.status === 401 ? null : `操作未保存:${e.message}`
    else if (authToken()) message = '网络异常,操作未保存'
    if (message) void import('@/app/shell').then(({ say }) => say(message))
  })
}
