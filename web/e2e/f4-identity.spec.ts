import { type Page } from '@playwright/test'
import { expect, test } from './fixtures'
import { openPage } from './support'

/**
 * F4 全景图按登录身份展示:医保局端身份看「市医保局」视角,医院身份看「本院」视角;
 * 不再提供视角切换,地址里的 ?who= 只会被规范成自己的视角。
 */
const header = (page: Page) => page.locator('header').first()
const identity = (page: Page) => page.getByTestId('cockpit-identity')

async function loginAs(page: Page, side: 'bureau' | 'org') {
  await page.addInitScript(() => sessionStorage.clear())
  await page.goto('/#/A1')
  await page.getByTestId(`side-${side}`).click()
  await page.getByPlaceholder('证书 PIN 码').fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByTestId('enter').click()
}

test.describe('F4 全景图按登录身份展示', () => {
  test('医保局端(召集人):只有市医保局视角,没有切换入口,顶栏是登录的人', async ({ page }) => {
    await openPage(page, 'cockpit', 'who=conv')
    await expect(page.getByText('当前视角')).toBeVisible()
    await expect(identity(page)).toContainText('市医保局')
    await expect(page.getByText('定点医药机构', { exact: true })).toHaveCount(0)
    await expect(page.getByText('医保数据公开全景图', { exact: true }).first()).toBeVisible()
    await expect(header(page)).toContainText('陈志远')
    await expect(header(page)).toContainText('召集人')
    // 市医保局视角能看到全市机构的预警
    await expect(page.getByText('某肛肠专科医院').first()).toBeVisible()
  })

  test('医保局端:手改地址 ?who=hosp 也看不到机构视角,地址被规范回 conv', async ({ page }) => {
    await openPage(page, 'cockpit', 'who=hosp')
    await expect(page).toHaveURL(/[?&]who=conv\b/)
    await expect(identity(page)).toContainText('市医保局')
    await expect(page.getByText('本院医保数据公开全景图')).toHaveCount(0)
  })

  test('机构端(医院身份):只有本院视角,机构名取自所属医院,看不到他院预警', async ({ page }) => {
    await loginAs(page, 'org')
    await page.goto('/#/cockpit?who=conv') // 即使指定医保局视角,也只会得到本院视角
    await expect(page.locator('[data-screen-label="01 全景图"]')).toBeVisible({ timeout: 15_000 })
    await expect(page).toHaveURL(/[?&]who=hosp\b/)
    await expect(identity(page)).toContainText('示例市第一人民医院')
    await expect(page.getByText('本院医保数据公开全景图').first()).toBeVisible()
    await expect(page.getByText('市医保局', { exact: true })).toHaveCount(0)
    await expect(page.getByText('某肛肠专科医院')).toHaveCount(0)
    await expect(page.getByText('甲县人民医院')).toHaveCount(0)
  })

  for (const side of ['bureau', 'org'] as const) {
    test(`实时提醒(${side === 'bureau' ? '医保局端' : '机构端'}):窗口内的每一条都完整可见(不被剪断);指标卡带走势折线`, async ({ page }) => {
      await loginAs(page, side)
      await page.goto('/#/cockpit')
      await expect(page.locator('[data-screen-label="01 全景图"]')).toBeVisible({ timeout: 15_000 })
      await page.waitForTimeout(800)
      await expect(page.getByTestId('cockpit-kpis').locator('svg')).toHaveCount(6)
      const box = await page.getByTestId('cockpit-alerts').boundingBox()
      expect(box).not.toBeNull()
      const rows = page.getByTestId('cockpit-alerts').locator('.border-t')
      const first = await rows.first().boundingBox()
      // 第一屏 4 行(或不足 4 条时全部)必须落在面板内部
      const n = Math.min(4, await rows.count())
      for (let i = 0; i < n; i++) {
        const r = await rows.nth(i).boundingBox()
        expect(r!.y + r!.height, `${side} 第 ${i + 1} 条被剪断`).toBeLessThanOrEqual(box!.y + box!.height - 2)
      }
      expect(first!.y).toBeGreaterThan(box!.y)
    })
  }
})
