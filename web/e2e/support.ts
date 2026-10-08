import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'

/** Real account used for direct API calls (logged in with the demo certificate PIN; 陈志远 · 召集人). */
export const E2E_USER = process.env.E2E_USER ?? 'chenzy'
const E2E_PIN = process.env.E2E_PIN ?? '123456'

const tokens = new WeakMap<APIRequestContext, Map<string, string>>()

/** demo accounts that log in on the 机构 side (everyone else is 医保局) — a wrong side would count as a failed login */
const ORG_USERS = new Set(['limin'])

/** Bearer token of `user` (demo certificate PIN) — null when the backend is unreachable. */
async function apiToken(request: APIRequestContext, user = E2E_USER): Promise<string | null> {
  const cached = tokens.get(request)?.get(user)
  if (cached) return cached
  try {
    const side = ORG_USERS.has(user) ? 'org' : 'bureau'
    const r = await request.post('/api/v1/auth/login', { data: { method: 'cert', account: user, pin: E2E_PIN, side } })
    const token = r.ok() ? ((await r.json()) as { token?: string }).token : undefined
    if (!token) return null
    if (!tokens.has(request)) tokens.set(request, new Map())
    tokens.get(request)!.set(user, token)
    return token
  } catch {
    return null // backend down
  }
}

async function apiHeaders(request: APIRequestContext, user = E2E_USER): Promise<Record<string, string>> {
  const token = await apiToken(request, user)
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** All routes of the app (web/src/app/pages.ts — 25 screens). */
export const ROUTES = [
  'cockpit', 'A1', 'A3', 'A4', 'A5', 'A6', 'A7', 'A8', 'A9', 'A10', 'A11', 'A12', 'A13', 'A14', 'A15',
  'B1', 'B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'C3', 'D1',
] as const
export type Route = (typeof ROUTES)[number]

/**
 * Open a page by hash route and wait until its screen has rendered and the
 * backend read model (`GET /api/v1/pages/{code}`) has been applied.
 */
export async function openPage(page: Page, code: Route, query = ''): Promise<void> {
  const pageData = page
    .waitForResponse(r => r.url().includes(`/api/v1/pages/${code}`) && r.request().method() === 'GET', { timeout: 10_000 })
    .catch(() => null)
  await page.goto(`/#/${code}${query ? `?${query}` : ''}`)
  await expect(page.locator('[data-screen-label]').first()).toBeVisible({ timeout: 15_000 })
  const res = await pageData
  if (res) await page.waitForTimeout(150) // let the payload re-render
}

/** Reload the current page and wait for the screen + read model again. */
export async function reloadPage(page: Page, code: Route): Promise<void> {
  const pageData = page
    .waitForResponse(r => r.url().includes(`/api/v1/pages/${code}`) && r.request().method() === 'GET', { timeout: 10_000 })
    .catch(() => null)
  await page.reload()
  await expect(page.locator('[data-screen-label]').first()).toBeVisible({ timeout: 15_000 })
  if (await pageData) await page.waitForTimeout(150)
}

/** The bottom-centre sonner toast containing `text`. */
export function toast(page: Page, text: string | RegExp): Locator {
  return page.locator('[data-sonner-toast]').filter({ hasText: text })
}

/** Wait for the next `POST /api/v1/actions/{code}/{action}` the page sends. */
export function waitForAction(page: Page, code: string, action: string) {
  return page.waitForRequest(
    r => r.method() === 'POST' && r.url().includes(`/api/v1/actions/${code}/${action}`),
    { timeout: 10_000 },
  )
}

/** POST an action straight to the core API (used for demo resets). Never throws. Role-checked actions need a real
 *  user login as `user` (e.g. 'chenzy' for A8/resetDemo). */
export async function postAction(request: APIRequestContext, code: string, action: string, payload: unknown = {}, user = E2E_USER) {
  try {
    const r = await request.post(`/api/v1/actions/${code}/${action}`, {
      data: payload,
      headers: await apiHeaders(request, user),
    })
    return r.status()
  } catch {
    return 0
  }
}

/** GET a page read model from the core API, or null when the backend is unreachable. */
export async function getPageModel<T = Record<string, unknown>>(request: APIRequestContext, code: string, user = E2E_USER): Promise<T | null> {
  try {
    const r = await request.get(`/api/v1/pages/${code}`, { headers: await apiHeaders(request, user) })
    if (!r.ok()) return null
    const ct = r.headers()['content-type'] ?? ''
    return ct.includes('json') ? ((await r.json()) as T) : null
  } catch {
    return null
  }
}

/** True when the live core backend answers (flows that assert server state need it). */
export async function backendUp(request: APIRequestContext): Promise<boolean> {
  return (await getPageModel(request, 'cockpit')) != null
}
