import { type ConsoleMessage } from '@playwright/test'
import { expect, test } from './fixtures'
import { ROUTES } from './support'

/**
 * Pad 适配:横屏 / 竖屏下每个页面都不能出现整页横向滚动,也不能有控制台错误;
 * 抽屉菜单、点按展开的一级菜单、竖屏全息图提示在触屏下可用。
 * 登录为陈志远(召集人)——可打开全部页面,含机构端页面。
 */
const PADS = [
  { name: '横屏 1024×768', width: 1024, height: 768 },
  { name: '横屏 1180×820', width: 1180, height: 820 },
  { name: '竖屏 768×1024', width: 768, height: 1024 },
  { name: '竖屏 820×1180', width: 820, height: 1180 },
] as const

const IGNORED = [/favicon/i, /\/api\/v1\/analytics\//, /status of (502|503|504)/, /Failed to load resource/]

test.describe('Pad 视口:无整页横向滚动、无控制台错误', () => {
  for (const vp of PADS) {
    test.describe(vp.name, () => {
      test.use({ viewport: { width: vp.width, height: vp.height }, hasTouch: true })

      for (const code of ROUTES) {
        if (code === 'A1') continue // 登录页单独测试
        test(`#/${code}`, async ({ page }) => {
          const errors: string[] = []
          page.on('pageerror', e => errors.push(e.message))
          page.on('console', (m: ConsoleMessage) => {
            if (m.type() === 'error' && !IGNORED.some(r => r.test(m.text()))) errors.push(m.text())
          })
          await page.goto(`/#/${code}`)
          await expect(page.locator('[data-screen-label]').first()).toBeVisible({ timeout: 15_000 })
          await page.waitForTimeout(600) // 等后端数据替换种子后重新排版
          const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
          expect(over, `整页横向溢出 ${over}px`).toBeLessThanOrEqual(0)
          expect(errors).toEqual([])
        })
      }

      test('登录页 #/A1', async ({ page }) => {
        await page.context().clearCookies()
        await page.addInitScript(() => sessionStorage.clear())
        await page.goto('/#/A1')
        await expect(page.getByTestId('side-bureau')).toBeVisible()
        const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
        expect(over).toBeLessThanOrEqual(0)
      })
    })
  }
})

test.describe('触屏菜单', () => {
  test.use({ viewport: { width: 768, height: 1024 }, hasTouch: true })

  test('竖屏:点汉堡打开抽屉,点页面跳转并自动关闭', async ({ page }) => {
    await page.goto('/#/A4')
    await expect(page.getByTestId('nav-toggle')).toBeVisible()
    await page.getByTestId('nav-toggle').tap()
    const drawer = page.getByTestId('nav-drawer')
    await expect(drawer).toBeVisible()
    await drawer.getByText('图表与报告模板').first().tap()
    await expect(page).toHaveURL(/#\/A5/)
    await expect(drawer).toBeHidden()
  })

  test('竖屏全息图:出现「建议横屏」提示,关闭后同一会话内不再出现', async ({ page }) => {
    await page.goto('/#/cockpit')
    const tip = page.getByText('建议横屏查看')
    await expect(tip).toBeVisible({ timeout: 15_000 })
    await page.getByRole('button', { name: '关闭提示' }).tap()
    await expect(tip).toBeHidden()
    await page.reload()
    await expect(page.locator('[data-screen-label]').first()).toBeVisible({ timeout: 15_000 })
    await expect(tip).toBeHidden()
  })
})

test.describe('触屏菜单(横屏)', () => {
  test.use({ viewport: { width: 1180, height: 820 }, hasTouch: true })

  test('一级菜单点按展开、点空白关闭', async ({ page }) => {
    await page.goto('/#/A4')
    const insight = page.getByRole('button', { name: '洞察' }).first()
    await insight.tap()
    await expect(page.getByText('智能推荐').first()).toBeVisible()
    await page.mouse.click(600, 600)
    await expect(page.getByRole('menuitem', { name: /智能推荐/ })).toHaveCount(0)
  })
})
