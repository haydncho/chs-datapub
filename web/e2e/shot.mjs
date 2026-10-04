// 截图辅助（演示模式）：node e2e/shot.mjs <ca|账号> <页面编号...>
// 环境变量：BASE（默认 http://localhost:5173）、THEME=light|dark、SHOT_LOGIN=1（同时截登录页）、FULL=1（整页）、CHROME（浏览器路径）
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = process.env.BASE ?? 'http://localhost:5173'
const [who = 'ca', ...pages] = process.argv.slice(2)
const theme = process.env.THEME ?? 'light'
mkdirSync('e2e/out', { recursive: true })

const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
await ctx.addInitScript((t) => localStorage.setItem('dpub.theme', t), theme)
const p = await ctx.newPage()
p.on('pageerror', (e) => console.log('PAGEERROR', e.message))
p.on('console', (m) => m.type() === 'error' && console.log('CONSOLE', m.text()))

await p.goto(BASE + '/login')
await p.waitForSelector('[data-testid=login-card]')
await p.waitForTimeout(600)
if (process.env.SHOT_LOGIN) await p.screenshot({ path: `e2e/out/A1-login-${theme}.png` })
if (who === 'ca') {
  await p.fill('#lg-pin', '123456')
} else {
  await p.click('text=账号 + 短信验证码')
  await p.fill('#lg-user', who)
  await p.fill('#lg-pwd', 'demo')
  await p.fill('#lg-code', '482916')
}
await p.click('[data-testid=login-submit]')
const idStep = await p.waitForSelector('[data-testid=identity-step]', { timeout: 3000 }).catch(() => null)
if (idStep) {
  if (process.env.SHOT_LOGIN) await p.screenshot({ path: `e2e/out/A1-identity-${theme}.png` })
  await p.click('[data-testid=enter-btn]')
}
await p.waitForSelector('[data-testid=side-nav]')
for (const pg of pages) {
  await p.goto(`${BASE}/${pg.toLowerCase()}`)
  await p.waitForTimeout(1200)
  await p.screenshot({ path: `e2e/out/${pg}-${theme}.png`, fullPage: !!process.env.FULL })
  console.log('shot', pg)
}
await browser.close()
