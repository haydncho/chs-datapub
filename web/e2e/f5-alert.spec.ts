import { type Page } from '@playwright/test'
import { expect, test } from './fixtures'
import { backendUp, getPageModel, openPage, reloadPage, toast, waitForAction } from './support'

/**
 * F5 预警: A11 发送提醒函 → D1 机构回执 (and 月报签收) → after a reload the
 * backend overlays show the new state on A11 (提醒函已发 / 已回执) and B4 (已签收).
 *
 * Server state is not reset between runs (there is no demo reset for alerts),
 * so the spec adapts: it sends a reminder for whichever alert is still 未处置.
 */
interface A11Model { alerts: { id: string; org: string; metric: string; status: 'unsent' | 'sent' | 'ack' }[] }
interface B4Model { reports: { name: string; status: string }[] }

const alertRow = (page: Page, metric: string) =>
  page.locator('[data-screen-label="A11 预警提醒"] div.grid.cursor-pointer').filter({ hasText: metric })

test.describe('F5 预警', () => {
  test.beforeEach(async ({ request }) => {
    test.skip(!(await backendUp(request)), 'F5 asserts backend overlays — needs a live core service')
  })

  test('A11 发提醒函 → reload keeps 提醒函已发 · 待回执', async ({ page, request }) => {
    const before = await getPageModel<A11Model>(request, 'A11')
    const target = before?.alerts.find(a => a.status === 'unsent')
    test.skip(!target, 'no 未处置 alert left in this database (re-create the e2e DB to re-run this step)')

    await openPage(page, 'A11')
    await alertRow(page, target!.metric).click()
    const send = waitForAction(page, 'A11', 'sendReminder')
    const res = page.waitForResponse(r => r.url().includes('/api/v1/actions/A11/sendReminder'))
    await page.getByRole('button', { name: '发送提醒函' }).click()
    expect((await send).postDataJSON()).toMatchObject({ alertId: target!.id })
    expect((await res).ok()).toBeTruthy()
    await expect(toast(page, `提醒函已发送至 ${target!.org}`)).toBeVisible()
    await expect(alertRow(page, target!.metric)).toContainText('提醒函已发 · 待回执')

    await reloadPage(page, 'A11')
    await expect(alertRow(page, target!.metric)).toContainText('提醒函已发 · 待回执')
    const after = await getPageModel<A11Model>(request, 'A11')
    expect(after?.alerts.find(a => a.id === target!.id)?.status).toBe('sent')
  })

  test('D1 回执 + 签收 → A11 已回执 and B4 已签收 after reload', async ({ page, request }) => {
    // D1 home → IU29 提醒函 → 填写回执说明 → 提交回执
    await openPage(page, 'D1')
    const phone = page.locator('[data-screen-label="D1 移动端"]')
    await phone.getByText('IU29 提醒函 · 需回执', { exact: true }).first().click()
    await expect(phone.getByText('IU29 例均基金差额超阈值').first()).toBeVisible()
    await phone.getByRole('button', { name: '填写回执说明', exact: true }).click()

    // submit is refused until a category and a text are given
    await phone.getByRole('button', { name: '提交回执', exact: true }).click()
    await expect(phone.getByText('请选择原因类别并填写说明').first()).toBeVisible()

    await phone.getByText('编码调整', { exact: true }).first().click()
    await phone.getByPlaceholder('简述原因与整改措施').fill('e2e:已组织编码培训,10 月起清单上传前院内预审。')
    const receipt = page.waitForResponse(r => r.url().includes('/api/v1/actions/D1/submitReceipt'))
    await phone.getByRole('button', { name: '提交回执', exact: true }).click()
    expect((await receipt).ok(), 'D1 submitReceipt accepted by core').toBeTruthy()
    await expect(phone.getByText('回执已提交', { exact: true }).first()).toBeVisible()
    await phone.getByRole('button', { name: '返回首页', exact: true }).click()

    // D1 report → 确认签收
    await phone.getByText('8 月月度报告待签收', { exact: true }).first().click()
    const sign = page.waitForResponse(r => r.url().includes('/api/v1/actions/D1/signReport'))
    await phone.getByRole('button', { name: '确认签收', exact: true }).click()
    const signRes = await sign
    // a repeat run may find the report already signed — the core may refuse a second sign-off
    expect(signRes.status(), 'D1 signReport').toBeLessThan(500)
    await expect(phone.getByText('已签收', { exact: true }).first()).toBeVisible()

    // A11: the IU29 alert now shows 已回执 · 整改中 (backend overlay)
    const a11 = await getPageModel<A11Model>(request, 'A11')
    const iu29 = a11?.alerts.find(a => a.metric.startsWith('IU29'))
    expect(iu29?.status).toBe('ack')
    await openPage(page, 'A11')
    await expect(alertRow(page, 'IU29')).toContainText('已回执 · 整改中')
    await reloadPage(page, 'A11')
    await expect(alertRow(page, 'IU29')).toContainText('已回执 · 整改中')
    await alertRow(page, 'IU29').click()
    await expect(page.getByRole('button', { name: '发送提醒函' })).toBeHidden()

    // B4: the 8 月 monthly report is 已签收 (signed on the phone)
    const b4 = await getPageModel<B4Model>(request, 'B4')
    const aug = b4?.reports.find(r => r.name.includes('2026年8月') && r.name.includes('月度'))
    expect(aug?.status).toBe('signed')
    await openPage(page, 'B4')
    await expect(page.getByText(aug!.name).first()).toBeVisible()
    await reloadPage(page, 'B4')
    const row = page.getByText(aug!.name).first()
    await row.click()
    await expect(page.getByText(/✓ 已签收 · 回执已发送/)).toBeVisible()
  })
})
