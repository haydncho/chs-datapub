import { ref, type Ref } from 'vue'
import { authToken, clearSession, landingOf, session } from '@/app/session'

/**
 * Thin API client.
 *
 * Every page renders immediately from its local seed (`src/mock/<page>.ts`,
 * the same demo data the backend seeds into PostgreSQL) and then swaps in the
 * server copy from `GET /api/v1/pages/{code}` when the backend is reachable.
 * Actions are fire-and-forget `POST /api/v1/actions/{code}/{action}` so the
 * UI stays responsive offline / without a backend.
 *
 * When logged in (A1), every call carries `Authorization: Bearer <token>`.
 * A 401 from the backend ends the session and routes to `#/A1`; a 403 on a
 * page payload returns to the identity's own landing page.
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
    if (res.status === 401 && isJson && !NO_REDIRECT.includes(path)) onUnauthorized()
    throw new ApiError(res.status, msg, isJson, retryAfter)
  }
  const text = await res.text()
  return text ? (JSON.parse(text) as T) : null
}

function onUnauthorized() {
  clearSession()
  void import('@/app/router').then(({ router, goPage }) => {
    if (router.currentRoute.value.meta.code !== 'A1') goPage('A1')
  })
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

/** Record a user action on the server (audit trail + state). Never throws. */
export function sendAction(code: string, action: string, payload?: unknown): void {
  postJson(`/actions/${encodeURIComponent(code)}/${encodeURIComponent(action)}`, payload ?? {}).catch(() => {})
}
