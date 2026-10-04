import type { ApiError } from './types'

const TOKEN_KEY = 'dpub.token'

export class HttpError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, body: Partial<ApiError> | null | undefined) {
    super(typeof body?.message === 'string' && body.message ? body.message : `请求失败（${status}）`)
    this.status = status
    this.code = typeof body?.code === 'string' && body.code ? body.code : 'HTTP_' + status
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* 隐私模式下忽略 */
  }
}

let onUnauthorized: () => void = () => {}

/** 由 auth store 注册：会话失效（401）时清理并回到登录页。 */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

type Query = Record<string, string | number | boolean | null | undefined>

function buildUrl(path: string, query?: Query) {
  const url = new URL(`/api/v1${path}`, window.location.origin)
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null || v === '') continue
      url.searchParams.set(k, String(v))
    }
  }
  return url.pathname + url.search
}

async function request<T>(method: string, path: string, body?: unknown, query?: Query): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), 60_000)
  let res: Response
  try {
    res = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: ctrl.signal,
    })
  } catch {
    throw new HttpError(0, { code: 'NETWORK', message: '网络异常或请求超时，请稍后重试' })
  } finally {
    clearTimeout(timer)
  }
  // 登录接口的 401 是「账号或密码错误」，不是会话失效
  if (res.status === 401 && !path.startsWith('/auth/login') && !path.startsWith('/auth/identity')) onUnauthorized()
  if (!res.ok) {
    let err: Partial<ApiError> | null = null
    try {
      err = (await res.json()) as Partial<ApiError>
    } catch {
      /* 非 JSON 错误体 */
    }
    throw new HttpError(res.status, err)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const http = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, undefined, query),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {}),
  del: <T>(path: string) => request<T>('DELETE', path),
}
