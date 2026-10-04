import { existsSync } from 'node:fs'
import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests for the 五条串联流程 + route smoke test.
 *
 * Expects the web app (vite dev or `vite preview`) to be running with `/api`
 * proxied to a live core service:
 *   E2E_BASE_URL=http://localhost:5305 npm run test:e2e
 * (defaults to http://localhost:5173). CI: the `e2e` job in .github/workflows/ci.yml.
 */
const CHROMIUM = process.env.PW_CHROMIUM ?? '/opt/pw-browsers/chromium'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 8_000 },
  // flows mutate shared backend state (A8 approval, A11 alerts, B4 reports) — run serially
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  outputDir: process.env.E2E_OUTPUT_DIR ?? 'test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        launchOptions: existsSync(CHROMIUM) ? { executablePath: CHROMIUM } : {},
      },
    },
  ],
})
