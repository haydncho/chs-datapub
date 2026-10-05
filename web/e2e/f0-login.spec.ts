import { expect, test } from '@playwright/test'
import { backendUp } from './support'

/**
 * 流程 0:登录是首页。未登录一律回登录页;医保局端与机构端分别选端、登录、选身份,进入各自的页面。
 * 这里用不带预置会话的原生 test。
 */
test.describe('登录页作为首页', () => {
  test.beforeEach(async ({ request }) => {
    test.skip(!(await backendUp(request)), '需要后端')
  })

  test('未登录访问任何页面都回到登录页,并记住回跳目标', async ({ page }) => {
    await page.goto('/#/A3')
    await expect(page).toHaveURL(/#\/A1\?redirect=%2FA3|#\/A1\?redirect=\/A3/)
    await page.goto('/')
    await expect(page).toHaveURL(/#\/A1/)
    await expect(page.getByTestId('side-bureau')).toBeVisible()
    await expect(page.getByTestId('side-org')).toBeVisible()
  })

  test('医保局端:陈志远只看到医保局端的身份,进入后是医保局端菜单', async ({ page }) => {
    await page.goto('/#/A1')
    await page.getByTestId('side-bureau').click()
    await page.getByPlaceholder('证书 PIN 码').fill('123456')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    const cards = page.getByTestId('identity-card')
    await expect(cards.first()).toBeVisible()
    expect(await cards.count()).toBe(2) // 召集人、行政管理组
    await cards.first().click()
    await page.getByTestId('enter').click()
    await expect(page).toHaveURL(/#\/cockpit/)
    await expect(page.getByRole('navigation').getByText('归集')).toBeVisible() // 医保局端才有的主线分组
  })

  test('机构端:陈志远只看到医院身份,进入后是机构端菜单', async ({ page }) => {
    await page.goto('/#/A1')
    await page.getByTestId('side-org').click()
    await page.getByPlaceholder('证书 PIN 码').fill('123456')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    const cards = page.getByTestId('identity-card')
    await expect(cards.first()).toBeVisible()
    expect(await cards.count()).toBe(1)
    await page.getByTestId('enter').click()
    await expect(page).toHaveURL(/#\/(cockpit|B1)/)
    await expect(page.getByRole('navigation').getByText('归集')).toHaveCount(0)
    await expect(page.getByRole('navigation').getByText('洞察')).toBeVisible()
  })

  test('机构端身份直接访问医保局端页面会被送回自己的落地页', async ({ page }) => {
    await page.goto('/#/A1')
    await page.getByTestId('side-org').click()
    await page.getByPlaceholder('证书 PIN 码').fill('123456')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.getByTestId('enter').click()
    await page.goto('/#/A3')
    await expect(page).not.toHaveURL(/#\/A3/)
  })
})
