import { type Page } from '@playwright/test'
import { expect, test } from './fixtures'
import { openPage } from './support'

/**
 * F4 身份切换: 全息图 查看身份 市医保局 ↔ 定点医药机构, kept in the URL (`?who=`).
 * The header identity block (AppShell, owned by auth) is matched loosely by name.
 */
const header = (page: Page) => page.locator('header').first()
const idTab = (page: Page, label: string) => page.locator('[data-screen-label="01 全息图"]').getByText(label, { exact: true })

test.describe('F4 身份切换', () => {
  test('市医保局 → 定点医药机构 → 市医保局, URL ?who= follows', async ({ page }) => {
    await openPage(page, 'cockpit', 'who=conv')
    await expect(page.getByText('查看身份')).toBeVisible()
    await expect(header(page)).toContainText('陈志远')
    await expect(page.getByText('医保数据全息图', { exact: true }).first()).toBeVisible()

    // → 定点医药机构
    await idTab(page, '定点医药机构').click()
    await expect(page).toHaveURL(/[?&]who=hosp\b/)
    await expect(header(page)).toContainText('李敏')
    await expect(header(page)).toContainText('医保办主任')
    await expect(page.getByText('本院医保数据全息图').first()).toBeVisible()

    // → 市医保局
    await idTab(page, '市医保局').click()
    await expect(page).toHaveURL(/[?&]who=conv\b/)
    await expect(header(page)).toContainText('陈志远')
    await expect(header(page)).not.toContainText('李敏')
  })

  test('deep link ?who=hosp opens the 机构 identity; query edits switch it, leaving the page drops it', async ({ page }) => {
    await openPage(page, 'cockpit', 'who=hosp')
    await expect(header(page)).toContainText('李敏')
    await expect(page.getByText('本院医保数据全息图').first()).toBeVisible()

    // prototype alias ?who=org maps to the same identity
    await page.goto('/#/cockpit?who=org')
    await expect(header(page)).toContainText('李敏')

    // changing the query by hand switches identity without a reload
    await page.goto('/#/cockpit?who=conv')
    await expect(header(page)).toContainText('陈志远')

    // 离开全息图后身份覆盖被丢弃,顶栏回到登录会话的身份(陈志远 · 召集人)
    await idTab(page, '定点医药机构').click()
    await expect(header(page)).toContainText('李敏')
    await openPage(page, 'A3')
    await expect(header(page)).not.toContainText('李敏')
    await expect(header(page)).toContainText('陈志远')
  })
})
