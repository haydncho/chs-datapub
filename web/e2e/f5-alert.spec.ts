import { type Page } from '@playwright/test'
import { expect, test } from './fixtures'
import { backendUp, getPageModel, openPage, postAction, reloadPage, toast, waitForAction } from './support'

/** Act in the browser as another demo user (the fixture logs in as 陈志远; a later init script wins). */
async function loginAs(page: Page, account: string, side: 'bureau' | 'org') {
  const r = await page.request.post('/api/v1/auth/login', { data: { method: 'cert', account, pin: '123456', side } })
  expect(r.ok(), `login ${account}`).toBeTruthy()
  const session = JSON.stringify(await r.json())
  await page.addInitScript(([k, v]) => sessionStorage.setItem(k, v), ['yb.session', session])
  // already on the app: hash navigation keeps the document, so switch the stored session and reload now
  if (page.url().startsWith('http')) {
    await page.evaluate(([k, v]) => sessionStorage.setItem(k!, v!), ['yb.session', session])
    await page.reload()
  }
}

/**
 * F5 预警: A11 发送提醒函 → D1 机构回执 (and 月报签收) → after a reload the
 * backend overlays show the new state on A11 (提醒函已发 / 已回执) and B4 (已签收).
 *
 * Server state is not reset between runs (there is no demo reset for alerts),
 * so the spec adapts: it sends a reminder for whichever alert is still 未处置.
 */
interface A11Model { alerts: { id: string; org: string; metric: string; status: 'unsent' | 'sent' | 'ack'; receipt?: { text: string } }[] }
interface B4Model { reports: { name: string; status: string }[] }

const alertRow = (page: Page, metric: string, org?: string) => {
  const rows = page.locator('[data-screen-label="A11 预警提醒"] div.grid.cursor-pointer').filter({ hasText: metric })
  return org ? rows.filter({ hasText: org }) : rows
}

const H001 = '示例市第一人民医院'

test.describe('F5 预警', () => {
  test.beforeEach(async ({ request }) => {
    test.skip(!(await backendUp(request)), 'F5 asserts backend overlays — needs a live core service')
  })

  test('A11 发提醒函 → reload keeps 提醒函已发 · 待回执', async ({ page, request }) => {
    const before = await getPageModel<A11Model>(request, 'A11')
    const target = before?.alerts.find(a => a.status === 'unsent')
    test.skip(!target, 'no 未处置 alert left in this database (re-create the e2e DB to re-run this step)')

    await openPage(page, 'A11')
    await alertRow(page, target!.metric, target!.org).click()
    const send = waitForAction(page, 'A11', 'sendReminder')
    const res = page.waitForResponse(r => r.url().includes('/api/v1/actions/A11/sendReminder'))
    await page.getByRole('button', { name: '发送提醒函' }).click()
    expect((await send).postDataJSON()).toMatchObject({ alertId: target!.id })
    expect((await res).ok()).toBeTruthy()
    await expect(toast(page, `提醒函已发送至 ${target!.org}`)).toBeVisible()
    await expect(alertRow(page, target!.metric, target!.org)).toContainText('提醒函已发 · 待回执')

    await reloadPage(page, 'A11')
    await expect(alertRow(page, target!.metric, target!.org)).toContainText('提醒函已发 · 待回执')
    // a second reminder for the same alert is refused by the server
    expect(await postAction(request, 'A11', 'sendReminder', { alertId: target!.id })).toBe(400)
    const after = await getPageModel<A11Model>(request, 'A11')
    expect(after?.alerts.find(a => a.id === target!.id)?.status).toBe('sent')
  })

  test('D1 回执 + 签收 → A11 已回执 and B4 已签收 after reload', async ({ page, request }) => {
    const before = await getPageModel<A11Model>(request, 'A11')
    const own = before?.alerts.find(a => a.id === 'AL-07')
    test.skip(own?.status !== 'sent', 'the H001 IU29 提醒函 (AL-07) is already answered in this database (re-create the e2e DB to re-run)')
    // 未批准不外发: the 8 月 report reaches the institution only after 召集人批准 (m8 → 定向发布 incl. 第一人民医院)
    await postAction(request, 'A8', 'resetDemo', { taskId: 'm8' }, 'chenzy')
    expect(await postAction(request, 'A8', 'approvePublish', { taskId: 'm8', institutions: ['第一人民医院'] }, 'chenzy')).toBe(200)
    try {
      // the institution itself signs and answers (医保局账号不能代签): 李敏 · 示例市第一人民医院
      await loginAs(page, 'limin', 'org')

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

      // D1 report → 确认签收 (the server answers first, then the phone shows 已签收)
      await phone.getByText('8 月月度报告待签收', { exact: true }).first().click()
      const sign = page.waitForResponse(r => r.url().includes('/api/v1/actions/D1/signReport'))
      await phone.getByRole('button', { name: '确认签收', exact: true }).click()
      const signRes = await sign
      expect(signRes.ok(), 'D1 signReport').toBeTruthy()
      expect(signRes.request().postDataJSON()).toMatchObject({ reportId: 'R-2026-08' })
      await expect(phone.getByText('已签收', { exact: true }).first()).toBeVisible()

      // after a reload the phone still shows it signed and offers no second sign-off
      await reloadPage(page, 'D1')
      await phone.getByText('8 月月度报告待签收', { exact: true }).first().click()
      await expect(phone.getByText(/^已签收/).first()).toBeVisible()
      await expect(phone.getByRole('button', { name: '确认签收', exact: true })).toBeHidden()

      // B4 (same institution): the 8 月 monthly report is 已签收 (signed on the phone)
      const b4 = await getPageModel<B4Model>(request, 'B4', 'limin')
      const aug = b4?.reports.find(r => r.name.includes('2026年8月') && r.name.includes('月度'))
      expect(aug?.status).toBe('signed')
      await openPage(page, 'B4')
      await reloadPage(page, 'B4')
      await page.getByText(aug!.name).first().click()
      await expect(page.getByText(/✓ 已签收/)).toBeVisible()

      // A11 (医保局): AL-07 now shows 已回执 · 整改中 with the institution's own text; the other hospital's IU29 alert is untouched
      const a11 = await getPageModel<A11Model>(request, 'A11')
      const iu29 = a11?.alerts.find(a => a.id === 'AL-07')
      expect(iu29?.status).toBe('ack')
      expect(iu29?.receipt?.text).toContain('e2e:已组织编码培训')
      expect(a11?.alerts.find(a => a.metric.startsWith('IU29') && a.org !== H001)?.status).not.toBe('ack')
      await loginAs(page, 'chenzy', 'bureau')
      await openPage(page, 'A11')
      await expect(alertRow(page, 'IU29', H001)).toContainText('已回执 · 整改中')
      await reloadPage(page, 'A11')
      await expect(alertRow(page, 'IU29', H001)).toContainText('已回执 · 整改中')
      await alertRow(page, 'IU29', H001).click()
      await expect(page.getByRole('button', { name: '发送提醒函' })).toBeHidden()
      await expect(page.getByText(/e2e:已组织编码培训/)).toBeVisible()
    } finally {
      await postAction(request, 'A8', 'resetDemo', { taskId: 'm8' }, 'chenzy')
    }
  })
})
