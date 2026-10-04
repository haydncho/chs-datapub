import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'

/** Actor sent on direct API calls (dev header auth, `yb.auth.dev-header=true`). */
export const E2E_USER = process.env.E2E_USER ?? 'e2e'

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

/** POST an action straight to the core API (used for demo resets). Never throws. */
export async function postAction(request: APIRequestContext, code: string, action: string, payload: unknown = {}) {
  try {
    const r = await request.post(`/api/v1/actions/${code}/${action}`, {
      data: payload,
      headers: { 'X-YB-User': E2E_USER },
    })
    return r.status()
  } catch {
    return 0
  }
}

/** GET a page read model from the core API, or null when the backend is unreachable. */
export async function getPageModel<T = Record<string, unknown>>(request: APIRequestContext, code: string): Promise<T | null> {
  try {
    const r = await request.get(`/api/v1/pages/${code}`, { headers: { 'X-YB-User': E2E_USER } })
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
