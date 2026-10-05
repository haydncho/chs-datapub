import { type ConsoleMessage } from '@playwright/test'
import { expect, test } from './fixtures'
import { ROUTES, openPage } from './support'

/**
 * Smoke: every route renders, logs no console errors / uncaught exceptions and
 * has no horizontal page scroll at 1440×900.
 *
 * Network failures of optional upstreams are tolerated (logged as annotations):
 * a failed `/api/v1/analytics/**` call (analytics service not running) and the
 * browser's generic "Failed to load resource" line that accompanies it.
 */
const TOLERATED = [/\/api\/v1\/analytics\//, /\/favicon\.ico$/]

for (const code of ROUTES) {
  test(`route #/${code} renders cleanly`, async ({ page }, info) => {
    const errors: string[] = []
    const tolerated: string[] = []
    const failedUrls: string[] = []

    page.on('response', r => {
      if (r.status() >= 400) failedUrls.push(`${r.status()} ${r.url()}`)
    })
    page.on('console', (m: ConsoleMessage) => {
      if (m.type() !== 'error') return
      const text = m.text()
      const where = m.location()?.url ?? ''
      if (/Failed to load resource/.test(text) && TOLERATED.some(re => re.test(where))) {
        tolerated.push(text + ' ' + where)
        return
      }
      errors.push(`${text}${where ? ` (${where})` : ''}`)
    })
    page.on('pageerror', e => errors.push(`pageerror: ${e.message}`))

    await openPage(page, code)
    // give lazy panels / timers a moment to settle
    await page.waitForTimeout(600)

    // no horizontal page scroll
    const overflow = await page.evaluate(() => {
      const d = document.documentElement
      return { scroll: d.scrollWidth, client: d.clientWidth }
    })
    expect.soft(overflow.scroll, `horizontal scroll on #/${code}: scrollWidth ${overflow.scroll} > ${overflow.client}`)
      .toBeLessThanOrEqual(overflow.client)

    for (const t of tolerated) info.annotations.push({ type: 'tolerated console error', description: t })
    for (const u of failedUrls) info.annotations.push({ type: 'http error', description: u })
    expect.soft(errors, `console errors on #/${code}`).toEqual([])
  })
}
